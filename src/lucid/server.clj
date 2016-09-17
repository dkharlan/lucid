(ns lucid.server
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]))

(defn close! [streams-atom stream-id info]
  (swap! streams-atom dissoc stream-id)
  (log/debug "Connection from" (:remote-addr info) "closed"))

(defn handle-input! [stream-id bytes] ;; TODO need states-atom
  (let [message (-> bytes
                  (String. "UTF-8")
                  (clojure.string/trim))]
    (log/debug "Message from" (str stream-id ":") message)
    ;; TODO feed message, derefed streams into fsm (retrieved from states-atom)
    ;; TODO for each side effect:
    ;; TODO    if stream side effect, write to target stream
    ;; TODO    if db side effects, transact
    ;; TODO swap! states dissoc side effects
    ))

;; TODO need to create a server stream w/ a fixed thread executor with size 1, then
;; TODO all input messages go to this.
(defn accept-new-connection! [streams-atom stream info]
  (let [stream-id (uuid/v1)]
    (log/debug "Accepting new connection with details" info)
    (s/on-closed stream (partial close! streams-atom stream-id info))
    (s/consume (partial handle-input! stream-id) stream)
    (swap! streams-atom assoc stream-id stream)))

(defprotocol Server
  (stop-server! [server-info]))

(defrecord ServerInfo [streams tcp-server port]
  Server
  (stop-server! [{:keys [streams tcp-server]}]
    (doseq [stream (vals @streams)]
      (s/close! stream))
    (.close tcp-server)
    (log/info "Stopped server")))

(defn start-server! [port]
  (let [streams (atom {})
        server (tcp/start-server
                 (partial accept-new-connection! streams)
                 {:port port})]
    (log/info "Started server on port" port)
    (->ServerInfo streams server port)))

