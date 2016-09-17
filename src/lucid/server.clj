(ns lucid.server
  (:require [taoensso.timbre :as log]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [manifold.stream :as ex]))

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

;; TODO need a better name
(defn run-state-for-input! [{:keys [stream-id message session-details-atom]}]
  (let [message ]
    (log/debug "Message from" (str stream-id ":") message)
    ;; TODO feed message, derefed streams into fsm (retrieved from states-atom)
    ;; TODO for each side effect:
    ;; TODO    if stream side effect, write to target stream
    ;; TODO    if db side effects, transact
    ;; TODO swap! states dissoc side effects
    ))

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

(defprotocol Server
  (stop-server! [server-info]))

(defrecord ServerInfo [session-details-atom tcp-server port handler-stream]
  Server
  (stop-server! [{:keys [session-details-atom tcp-server]}]
    (let [{:keys [streams states]} @session-details-atom]
      (doseq [stream (vals @streams)]
        (s/close! stream))
      (.close tcp-server)
      (reset! session-details-atom nil)
      (log/info "Stopped server"))))

(defn start-server! [port]
  (let [session-details-atom (atom {})
        handler-stream (s/stream* {:executor (ex/fixed-thread-executor 1)})
        _ (s/consume handle-input! handler-stream)
        server (tcp/start-server
                 (partial accept-new-connection!
                   session-details-atom
                   handler-stream)
                 {:port port})]
    (log/info "Started server on port" port)
    (->ServerInfo session-details-atom server port)))

