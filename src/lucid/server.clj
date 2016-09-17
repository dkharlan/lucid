(ns lucid.server
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [manifold.stream :as ex]))

(defn close! [streams-atom stream-id info]
  (swap! streams-atom dissoc stream-id)
  (log/debug "Connection from" (:remote-addr info) "closed"))

(def bytes->string [bytes]
  (-> bytes
    (String. "UTF-8")
    (clojure.string/trim)))

;; TODO need a better name
(defn run-state-for-input! [{:keys [stream-id message streams]}]
  (let [message ]
    (log/debug "Message from" (str stream-id ":") message)
    ;; TODO feed message, derefed streams into fsm (retrieved from states-atom)
    ;; TODO for each side effect:
    ;; TODO    if stream side effect, write to target stream
    ;; TODO    if db side effects, transact
    ;; TODO swap! states dissoc side effects
    ))

(defn accept-new-connection! [handler-stream streams-atom stream info]
  (let [stream-id (uuid/v1)]
    (log/debug "Accepting new connection with details" info)
    (s/on-closed stream (partial close! streams-atom stream-id info))
    (s/connect-via
      stream
      #(s/put! handler-stream
         {:stream-id
          :message (bytes->string %)
          :streams @streams-atom})
      handler-stream)
    (swap! streams-atom assoc stream-id stream)))

(defprotocol Server
  (stop-server! [server-info]))

(defrecord ServerInfo [streams tcp-server port handler-stream]
  Server
  (stop-server! [{:keys [streams tcp-server]}]
    (doseq [stream (vals @streams)]
      (s/close! stream))
    (.close tcp-server)
    (log/info "Stopped server")))

(defn start-server! [port]
  (let [streams-atom (atom {})
        handler-stream (s/stream* {:executor (ex/fixed-thread-executor 1)})
        _ (s/consume handle-input! handler-stream)
        server (tcp/start-server
                 (partial accept-new-connection!
                   handler-stream
                   streams-atom)
                 {:port port})]
    (log/info "Started server on port" port)
    (->ServerInfo streams server port)))

