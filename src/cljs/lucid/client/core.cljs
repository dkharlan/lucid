(ns lucid.client.core
  (:require [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [>! <! put! close! go go-loop]]))

(go
  (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:8080/connect"
                                         {:format :str}))]
    (if error
      (console.error "Failed to establish websocket connection:" error)
      (do
        (def channel ws-channel) ;; for the REPL
        (go-loop []
          (if-let [value (<! ws-channel)]
            (let [{:keys [message error]} value]
              (if error
                (console.error error)
                (do
                  (console.log "Server said:" message)
                  (recur))))
            (console.log "Connection closed.")))))))

