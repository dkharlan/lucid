(ns lucid.server
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [reduce-fsm :as fsm]
            [lucid.util :as util]))

;; TODO will also need to keep track of connection state(s)
(defn make-descriptor [id stream]
  {:id id :stream stream})

(defn server* [descriptors message-buffer acceptor tcp-server update-thread updater-signal]
  {:descriptors descriptors
   :message-buffer message-buffer
   :acceptor acceptor
   :tcp-server tcp-server
   :update-thread update-thread
   :updater-signal updater-signal})

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
    true      (log/info "Update thread has received shutdown signal.")
    ::timeout (log/error "Update thread did not acknowledge shutdown signal!")
    false     (log/error "Failed to notify update thread of shutdown!")))

(defn close! [descriptor descriptor-id info]
  (swap! descriptor dissoc descriptor-id)
  (log/debug "Connection from" (:remote-addr info) "closed"))

(defn make-message [descriptor-id message]
  {:descriptor-id descriptor-id :message (util/bytes->string message)})

(defn accept-new-connection! [descriptors message-buffer stream info]
  (log/debug "New connection initiated:" info)
  (let [descriptor-id       (uuid/v1)
        make-message*       (partial make-message descriptor-id)
        insert-into-buffer! #(s/put! message-buffer (make-message* %))]
    (s/on-closed stream (partial close! descriptors descriptor-id info))
    (s/connect-via stream insert-into-buffer! message-buffer)
    (swap! descriptors assoc descriptor-id (make-descriptor descriptor-id stream))
    (log/info "Accepted new connection from descriptor" descriptor-id "at" (:remote-addr info))))

;; TODO see if there's an easier / more succint / more idiomatic way to do this
(defn- collect! [stream]
  (loop [messages []]
    (let [message @(s/try-take! stream nil 0 ::nothing)] 
      (if (or (nil? message) (= message ::nothing)) 
        messages
        (recur (conj messages message))))))

(defn update! [message-buffer updater-signal]
  (log/info "Update thread started.")
  (while (let [signal @(s/try-take! updater-signal nil 0 ::nothing)]
           (and (not= ::shutdown signal) (not (nil? signal))))
    (let [messages (collect! message-buffer)]
      (doseq [{:keys [descriptor-id message] :as msg} messages]
        ;; TODO input processing logic goes here
        (log/debug "Message:" msg)
        (log/info descriptor-id "says" (str "\"" message "\""))))
    ;; TODO replace with something more robust
    (Thread/sleep 100))
  ;; TODO any cleanup goes here
  (log/info "Update thread finished cleaning up."))

(defn make-server [port]
  (let [descriptors    (atom {})
        message-buffer (s/stream* {:permanent? true :buffer-size 1000}) ;; TODO may have to fiddle with the buffer length
        acceptor       (partial accept-new-connection! descriptors message-buffer)
        tcp-server     (delay (tcp/start-server acceptor {:port port}))
        updater-signal (s/stream) ;; TODO what properties does this stream need? could see a buffer being necessary
        updater        (partial update! message-buffer updater-signal)
        update-thread  (Thread. updater "update-thread")]
    (server* descriptors message-buffer acceptor tcp-server update-thread updater-signal)))

