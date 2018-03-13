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
          (let [{:keys [message error]} (<! ws-channel)]
            (if error
              (console.error error)
              (do
                (console.log "Server said:" message)
                (recur)))))))))

