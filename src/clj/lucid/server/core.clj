(ns lucid.server.core
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [reduce-fsm :as fsm]
            [datomic.api :as db]
            [lucid.server.telnet :refer [telnet-handler!]]
            [lucid.states :as st]
            [lucid.database :as ldb]
            [datomic.api :as d]))

(defn- make-descriptor [id stream info]
  {:id id :stream stream :info info})

;; TODO think about how to manage state when connection goes linkdead (as opposed to a character logging out)
(defn- close! [descriptors states descriptor-id info]
  (swap! descriptors dissoc descriptor-id)
  (swap! states dissoc descriptor-id)
  (log/info "Connection from" (:remote-addr info) "closed"))

(defn- make-message [descriptor-id message]
  {:descriptor-id descriptor-id :message message})

(defn- accept-new-connection! [descriptors states message-buffer stream info]
  (log/debug "New connection initiated:" info)
  (let [descriptor-id    (uuid/v1)
        make-message*    (partial make-message descriptor-id)
        message-handler! (partial telnet-handler!
                           (atom []) ;; TODO may want to keep track of the leftovers atom someday
                           message-buffer
                           make-message*)
        game             (st/game)]
    (s/on-closed stream (partial close! descriptors states descriptor-id info))
    (s/consume message-handler! stream)
    (swap! descriptors assoc descriptor-id (make-descriptor descriptor-id stream info))
    (swap! states assoc descriptor-id
      (fsm/fsm-event game {:type :telnet :descriptor-id descriptor-id}))
    (log/info "Accepted new connection from descriptor" descriptor-id "at" (:remote-addr info))))

;; TODO see if there's an easier / more succint / more idiomatic way to do this
(defn- collect! [stream]
  (loop [messages []]
    (let [message @(s/try-take! stream nil 0 ::nothing)] 
      (if (or (nil? message) (= message ::nothing)) 
        messages
        (recur (conj messages message))))))

;; TODO refactor for brevity / style
;; TODO catch and handle exceptions so they don't kill the update thread
(defn- update! [descriptors states db-connection message-buffer updater-signal]
  (log/info "Update thread started.")
  (while (let [signal @(s/try-take! updater-signal nil 0 ::nothing)]
           (and (not= ::shutdown signal) (not (nil? signal))))
    (let [messages (collect! message-buffer)]
      (doseq [{:keys [descriptor-id message] :as msg} messages]
        (log/trace "Message:" msg)
        (log/trace descriptor-id "says" (str "\"" message "\""))
        (let [states*                       @states
              descriptors*                  @descriptors
              state                         (get states* descriptor-id)
              input                         {;; TODO should this be the full server info structure?
                                             :server-info {:descriptors descriptors* :states states*}
                                             :message message}
              next-state                    (fsm/fsm-event state input)
              {db-transactions     :db
               log-side-effects    :log
               stream-side-effects :stream} (get-in next-state [:value :side-effects])]
          (log/trace "Previous state:" state)
          (log/trace "Next state:" next-state)
          (log/trace "Transactions:" db-transactions)
          (log/trace "Stream messages:" stream-side-effects)
          (log/trace "Log messages" log-side-effects)
          (if (not (empty? stream-side-effects))
            (log/trace "Sending" (count stream-side-effects) "messages"))
          (doseq [{:keys [destination message]} stream-side-effects]
            (let [destination-stream (get-in descriptors* [destination :stream])]
              (s/put! destination-stream  message)))
          (if (not (empty? db-transactions))
            (log/trace "Transacting" db-transactions))
          @(db/transact db-connection db-transactions)
          (doseq [{:keys [level messages]} log-side-effects]
            (->> messages
              (interpose " ")
              (apply str)
              (log/log level)))
          ;; TODO do this for all side effect types instead of explicitly for each
          (swap! states assoc descriptor-id
            (-> next-state
              (assoc-in [:value :side-effects :db] [])
              (assoc-in [:value :side-effects :stream] [])
              (assoc-in [:value :side-effects :log] [])))
          (when (= (:state next-state) :zombie)
            (if-let [character-name (get-in next-state [:value :login :character-name])]
              (log/info "Logging" (str "\"" character-name "\"") "out"))
            (s/close! (get-in descriptors* [descriptor-id :stream]))))))
    (Thread/sleep 100)) ;; TODO replace Thread/sleep with something more robust
  ;; TODO any cleanup goes here
  (log/info "Update thread finished cleaning up."))

(defn- server* [descriptors states message-buffer acceptor tcp-server update-thread updater-signal]
  {:descriptors    descriptors
   :states         states
   :message-buffer message-buffer
   :acceptor       acceptor
   :tcp-server     tcp-server
   :update-thread  update-thread
   :updater-signal updater-signal})

(defn make-server [port db-uri]
  (let [descriptors    (atom {})
        states         (atom {})
        message-buffer (s/stream* {:permanent? true :buffer-size 1000}) ;; TODO may have to fiddle with the buffer length
        acceptor       (partial accept-new-connection! descriptors states message-buffer)
        tcp-server     (delay (tcp/start-server acceptor {:port port}))
        db-connection  (db/connect db-uri)
        updater-signal (s/stream) ;; TODO what properties does this stream need? could see a buffer being necessary
        updater        (partial update! descriptors states db-connection message-buffer updater-signal)
        update-thread  (Thread. updater "update-thread")]
    (server* descriptors states message-buffer acceptor tcp-server update-thread updater-signal)))

(defn start! [{:keys [tcp-server update-thread] :as this}]
  (log/info "Launching update thread...")
  (.start update-thread)
  @tcp-server
  (log/info "TCP socket server has been started.")
  this)

(defn stop! [{:keys [descriptors tcp-server updater-signal]}]
  (doseq [{:keys [stream]} (vals @descriptors)]
    (s/close! stream))
  (.close @tcp-server)
  (log/info "TCP socket server shutdown.")
  (reset! descriptors nil)
  (case @(s/try-put! updater-signal ::shutdown 5000 ::timeout)
    true      (log/debug "Update thread has received shutdown signal.")
    ::timeout (log/error "Update thread did not acknowledge shutdown signal!")
    false     (log/error "Failed to notify update thread of shutdown!")))

