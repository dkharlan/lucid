(ns lucid.server
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [reduce-fsm :as fsm]))

(defn close! [session-details-atom stream-id info]
  (swap! session-details-atom
    #(-> %
       (update-in [:streams] dissoc stream-id)
       (update-in [:states] dissoc stream-id)))
  (log/debug "Connection from" (:remote-addr info) "closed"))

(def bytes->string [bytes]
  (-> bytes
    (String. "UTF-8")
    (clojure.string/trim)))

(defn accept-new-connection! [session-details-atom handler-stream fsm stream info]
  (let [stream-id (uuid/v1)]
    (log/debug "Accepting new connection with details" info)
    (s/on-closed stream (partial close! session-details-atom stream-id info))
    (s/connect-via
      stream
      #(s/put! handler-stream
         {:stream-id
          :message (bytes->string %)
          :session-details-atom session-details-atom})
      handler-stream)
    (swap! session-details-atom
      #(-> %
         (assoc-in [:streams stream-id] stream)
         (assoc-in [:states stream-id] (fsm {}))))))

;; TODO see long comment in lucid.states
(defn handle-message! [{:keys [stream-id message session-details-atom]}]
  (log/debug "Message from" (str stream-id ":") message)
  (let [session-details @session-details-atom
        fsm (get-in session-details [:states stream-id])
        next-state (fsm/fsm-event fsm message)]
    (if (= :zombie (:state next-state))
      ;; TODO append dissoc to side-effects
      )
    (doseq [side-effect (:side-effects next-state)]
      )
    
    ;; TODO return deferred that yields true when handler-stream is exhausted
    )
  ;; TODO feed message, derefed streams into fsm (retrieved from states-atom)
  ;; TODO for each side effect:
  ;; TODO    if stream side effect, write to target stream
  ;; TODO    if db side effects, transact
  ;; TODO swap! states dissoc side effects
  )

(defn start-server! [port]
  (let [session-details-atom (atom {})
        handler-stream (s/stream* {:executor (ex/fixed-thread-executor 1)})
        _ (s/consume handle-message! handler-stream)
        server (tcp/start-server
                 (partial accept-new-connection!
                   session-details-atom
                   handler-stream)
                 {:port port})]
    (log/info "Started server on port" port)
    (->ServerInfo session-details-atom server port)))

;; TODO old stuff above

(defprotocol StatefulServer
  (start-server! [server-info])
  (stop-server! [server-info]))

(defrecord Server [descriptors message-buffer acceptor tcp-server update-thread]
  StatefulServer
  (start-server [{:keys [tcp-server update-thread] :as this}]
    @tcp-server
    (.start update-thread)
    this)
  (stop-server! [{:keys [tcp-server update-thread]}]
    (throw (UnsupportedOperationException.))
    (comment
      (let [{:keys [streams states]} @session-details-atom]
         (doseq [stream (vals @streams)]
           (s/close! stream))
         (.close tcp-server)
         (reset! session-details-atom nil)
         (log/info "Stopped server")))))

(defn- collect! [stream]
  (loop [messages []]
    (let [message (s/take! stream :nothing)]
      (if (= message :nothing)
        messages
        (recur (conj messages messages))))))

(defn update! [message-buffer]
  (while true
    (let [messages (collect! message-buffer)]
      (doseq [{:keys [descriptor-id message]} messages]
        (println descriptor-id "says" message)))
    (Thread/sleep 100) ;; TODO replace this with something more robust
    ))

(defn make-server [port]
  (let [descriptors    (atom)
        message-buffer (s/buffered-stream 1000)
        acceptor       (partial accept-new-connection!
                         descriptors message-buffer)
        tcp-server     (delay
                         (tcp/start-server
                           acceptor
                           message-buffer))
        update-thread  (Thread.
                         (partial update! message-buffer)
                         "update-thread")]
    (->Server descriptors message-buffer acceptor tcp-server update-thread)))

