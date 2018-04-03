(ns lucid.server.core
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [datomic.api :as db]
            [lucid.server.http :as http]
            [lucid.server.telnet :as telnet]
            [lucid.server.methods :as m]
            [lucid.states.core :as st]
            [lucid.states.helpers :as sth]
            [lucid.database :as ldb]))

;; TODO rename :event-buffer to :event-transactor-queue -- it communicates its intent better

(defn- make-descriptor [id stream info]
  {:id id :stream stream :info info})

;; TODO think about how to manage state when connection goes linkdead (as opposed to a character logging out)
(defn- close! [descriptors states descriptor-id info]
  (swap! descriptors dissoc descriptor-id)
  (swap! states dissoc descriptor-id)
  (log/info "Connection from" (:remote-addr info) "closed"))

(defn- make-event [descriptor-id event-type event-data]
  {:descriptor-id descriptor-id
   :event-type    event-type
   :event-data    event-data})

;; TODO catch and log exceptions
;; TODO holy wow there are a lot of nested, closed over state buckets...
(defn- accept-new-connection! [db-connection descriptors states event-buffer stream info]
  (log/debug "New connection initiated:" info)
  (let [connection-type  (:type info)
        descriptor-id    (uuid/v1)
        make-event*      (partial make-event descriptor-id ::stream-input)
        message-handler! (m/make-message-handler connection-type event-buffer make-event*)
        closed-handler!  (partial close! descriptors states descriptor-id info)
        output-stream    (m/make-output-stream connection-type stream closed-handler!)
        game             (st/game)]
    (s/consume message-handler! stream)
    (swap! descriptors assoc descriptor-id (make-descriptor descriptor-id output-stream info))
    (swap! states assoc descriptor-id
      (sth/inc-fsm game {:descriptor-id descriptor-id}))
    (s/put! output-stream
      (db/q '[:find ?greeting .
              :where [:server/info :server/greeting ?greeting]]
        (db/db db-connection)))
    (s/put! output-stream "What is your name?")
    (log/info "Accepted new connection from descriptor" descriptor-id "at" (:remote-addr info))))

(defn- affect-streams! [{{{:keys [descriptors]} :server-info} :input
                         {{{stream-side-effects :stream} :side-effects} :value} :state
                         :as event-bundle}]
  (when-not (empty? stream-side-effects)
    (log/trace "Stream messages:" (vec stream-side-effects))
    (doseq [{:keys [destination message]} stream-side-effects]
      (let [destination-stream (get-in descriptors [destination :stream])]
        (s/put! destination-stream  message))))
  event-bundle)

(defn- persist-transactions! [{{{{db-transactions :db} :side-effects} :value} :state :as event-bundle}
                              db-connection]
  (when-not (empty? db-transactions)
    (log/trace "Transacting" db-transactions)
    @(db/transact db-connection db-transactions))
  event-bundle)

(defn- record-logs! [{{{{log-entries :log} :side-effects} :value} :state :as event-bundle}]
  (doseq [{:keys [level messages]} log-entries]
    (->> messages
      (interpose " ")
      (apply str)
      (log/log level)))
  event-bundle)

(defmulti transition-state!
  (fn [{:keys [descriptor-id]} _ _]
    descriptor-id))

(defmethod transition-state! :default [{{:keys [state] :as next-state} :state
                                        descriptor-id                    :descriptor-id}
                                       !states !descriptors]
  (if-not (= state :zombie) 
    (swap! !states assoc descriptor-id
      (-> next-state
        (assoc-in [:value :side-effects :db] [])
        (assoc-in [:value :side-effects :stream] [])
        (assoc-in [:value :side-effects :log] [])))
    (do
      (log/trace "Trying to remove session descriptor")
      (if-let [character-name (get-in next-state [:value :login :character-name])]
        (log/info "Logging" (str "\"" character-name "\"") "out"))
      (s/close! (get-in @!descriptors [descriptor-id :stream])))))

(defmethod transition-state! nil [_ _ _]
  (log/trace "No descriptor; skipping state transition"))

;; TODO see if there's an easier / more succint / more idiomatic way to do this
(defn- collect! [stream]
  (loop [items []]
    (let [item @(s/try-take! stream nil 0 ::nothing)] 
      (if (or (nil? item) (= item ::nothing)) 
        items
        (recur (conj items item))))))

(defmulti translate-event*
  (fn [{{:keys [event-type]} :input}]
    event-type))

(defn- bundle-event [{:keys [descriptor-id event-data event-type]} states descriptors db]
  (log/trace (str "Event" (if descriptor-id (format " from descriptor %s" descriptor-id) "") ":") event-type event-data)
  (let [server-info {:descriptors descriptors
                     :states      states
                     :db          db}
        input       {:server-info server-info
                     :event-type  event-type
                     :event-data  event-data}
        state       (get states descriptor-id)]
    {:descriptor-id descriptor-id
     :input         input
     :state         state}))

(defmethod translate-event* :default [{{:keys [event-type]} :input :as event-bundle}]
  (log/warn "Ignoring event bundle with unknown :event-type" event-type "-" event-bundle))

(defmethod translate-event* ::stream-input [{:keys [state input] :as event-bundle}]
  (assoc event-bundle :state (sth/inc-fsm state input)))

(defmethod translate-event* :async-stream-output [{{:keys [event-data]} :input :as event-bundle}]
  (assoc event-bundle :state
    {:value
     {:side-effects
      {:stream event-data}}}))

(defn translate-event [event-bundle]
  (log/trace "Translating event:" event-bundle)
  (translate-event* event-bundle))

;; TODO need more complete exception handling?
(defn update! [descriptors states db-connection event-buffer updater-signal]
  (log/info "Update thread started.")
  (loop []
      (let [signal @(s/try-take! updater-signal nil 0 ::nothing)]
        (when (and (not= ::shutdown signal) (not (nil? signal)))
          (doseq [event-info (collect! event-buffer)]
            (log/trace "Event info:" event-info)
            (try
              (-> event-info 
                (bundle-event @states @descriptors (db/db db-connection)) 
                (translate-event)  ;; <---- the stateless part
                (affect-streams!)
                (persist-transactions! db-connection)
                (record-logs!)
                (transition-state! states descriptors))
              (catch Exception ex
                ;; TODO call out that event processing continues with the next event
                ;; TODO make event-info :trace logged to prevent logging potentially sensitive details
                (log/error "Uncaught exception while handling event" event-info)
                (log/error ex))))
          (Thread/sleep 100) ;; TODO replace Thread/sleep with something more robust
          (recur))))
  (log/info "Update thread finished cleaning up."))

(defn- server* [descriptors states db-connection event-buffer tcp-server http-server update-thread updater-signal]
  {:descriptors    descriptors
   :states         states
   :db-connection  db-connection
   :event-buffer   event-buffer
   :tcp-server     tcp-server
   :http-server    http-server
   :update-thread  update-thread
   :updater-signal updater-signal})

(defn make-server [{:keys [ports db-uri]}]
  (let [descriptors    (atom {})
        states         (atom {})
        event-buffer   (s/stream* {:permanent? true :buffer-size 1000}) ;; TODO may have to fiddle with the buffer length
        db-connection  (db/connect db-uri)
        acceptor       (partial accept-new-connection! db-connection descriptors states event-buffer)
        updater-signal (s/stream) ;; TODO what properties does this stream need? could see a buffer being necessary
        updater        (partial update! descriptors states db-connection event-buffer updater-signal)
        update-thread  (Thread. updater "update-thread")
        http-server    (http/make-server acceptor
                         (or (:http ports) 8080))
        tcp-server     (telnet/make-server acceptor
                         (or (:tcp ports) 4000))]
    (server* descriptors states db-connection event-buffer tcp-server http-server update-thread updater-signal)))

(defn start! [{:keys [tcp-server http-server update-thread] :as this}]
  (log/info "Launching update thread...")
  (.start update-thread)

  @tcp-server
  (log/info "TCP socket server has been started.")

  @http-server
  (log/info "HTTP server has been started.")

  this)

(defn stop! [{:keys [descriptors tcp-server http-server updater-signal]}]
  (doseq [{:keys [stream]} (vals @descriptors)]
    (s/close! stream))
  (log/debug "Closed all connection streams.")

  (.close @tcp-server)
  (log/info "TCP socket server shutdown.")

  (.close @http-server)
  (log/info "HTTP server shutdown.")

  (reset! descriptors nil)
  (case @(s/try-put! updater-signal ::shutdown 5000 ::timeout)
    true      (log/debug "Update thread has received shutdown signal.")
    ::timeout (log/error "Update thread did not acknowledge shutdown signal!")
    false     (log/error "Failed to notify update thread of shutdown!")))

