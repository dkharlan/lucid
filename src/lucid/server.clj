(ns lucid.server
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [reduce-fsm :as fsm]
            [lucid.util :as util]))

;; TODO will also need to keep track of connection state(s)
(defrecord Descriptor [id stream])

(defprotocol StatefulServer
  (start! [server-info])
  (stop! [server-info]))

(defrecord Server [descriptors message-buffer acceptor tcp-server update-thread updater-signal]
  StatefulServer
  (start! [{:keys [tcp-server update-thread] :as this}]
    (.start update-thread)
    (log/info "Update thread started.")
    @tcp-server
    (log/info "TCP socket server has been started.")
    this)
  (stop! [{:keys [tcp-server update-thread]}]
    (doseq [{:keys [stream]} @descriptors]
      (s/close! stream))
    (.close @tcp-server)
    (log/info "TCP socket server shutdown.")
    (reset! descriptors nil)
    (s/put! updater-signal ::shutdown)
    (let [signal @(s/try-take! updater-signal nil 5000 ::shutdown-timeout)]
      (case signal
        ;; TODO how to handle ::shutdown-timeout case?
        ::shutdown-timeout (let [error-message "Update thread has not acknowledged shutdown signal."]
                             (log/error error-message)
                             (throw (ex-info error-message {:thread update-thread})))
        ::cleaned-up       (log/info "Update thread has been stopped.")
        (log/warn "Received unexpected signal" signal "from update thread.")))))

(defn close! [descriptor descriptor-id info]
  (swap! descriptor dissoc descriptor-id)
  (log/debug "Connection from" (:remote-addr info) "closed"))

(defn accept-new-connection! [descriptors message-buffer stream info]
  (let [descriptor-id (uuid/v1)]
    (log/debug "Accepting new connection with details" info)
    (s/on-closed stream (partial close! descriptors descriptor-id info))
    (s/connect-via
      stream
      #(s/put! message-buffer 
         {:descriptor-id descriptor-id :message (util/bytes->string %)})
      message-buffer)
    (swap! descriptors assoc (->Descriptor {:id descriptor-id :stream stream}))))

;; TODO see if there's an easier / more succint / more idiomatic way to do this
(defn- collect! [stream]
  (loop [messages []]
    (let [message @(s/take! stream ::nothing)]
      (if (= message ::nothing)
        messages
        (recur (conj messages messages))))))

;; TODO replace sleep with something more robust
(defn update! [message-buffer updater-signal]
  (log/info "Update thread started.")
  (while (not= ::shutdown @(s/take! updater-signal ::nothing))
    (log/debug "Checked for signals")
    (let [messages (collect! message-buffer)]
      (log/debug "Have" (count messages) "messages to process")
      (doseq [{:keys [descriptor-id message]} messages]
        (log/info descriptor-id "says" message)))
    (Thread/sleep 100)) 
  (log/info "Update thread stopping...")
  ;; TODO any cleanup goes here
  (s/put! updater-signal ::cleaned-up))

(defn make-server [port]
  (let [descriptors    (atom {})
        message-buffer (s/buffered-stream 1000) ;; TODO may have to fiddle with the buffer length
        acceptor       (partial accept-new-connection! descriptors message-buffer)
        tcp-server     (delay (tcp/start-server acceptor {:port port}))
        updater-signal (s/stream) ;; TODO what properties does this stream need?
        updater        (partial update! message-buffer updater-signal)
        update-thread  (Thread. updater "update-thread")]
    (->Server descriptors message-buffer acceptor tcp-server update-thread updater-signal)))

