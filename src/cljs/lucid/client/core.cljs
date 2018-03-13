(ns lucid.client.core
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [>! <! put! close! go go-loop]]))

;; TODO use timbre for logging
;; TODO add reagent and make a simple console and command entry control

(def websocket-connection-endpoint "ws://localhost:8080/connect")

(defn message-loop! [channel]
  (go-loop []
    (if-let [value (<! channel)]
      (let [{:keys [message error]} value]
        (if error
          (console.error error)
          (do
            (console.log "Server said:" message)
            (recur))))
      (console.log "Connection closed."))))

(defn connect! [url]
  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch url {:format :str}))]
      (if error
        (console.error "Failed to establish websocket connection:" error)
        (do
          (def channel ws-channel) ;; TODO remove me later; for REPL access
          (message-loop! ws-channel)))))) 

(.addEventListener js/document "DOMContentLoaded"
  (fn [_]
    (connect! websocket-connection-endpoint)))

