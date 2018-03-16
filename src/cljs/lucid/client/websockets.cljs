(ns lucid.client.websockets
  (:require [taoensso.timbre :as log]
            [cljs.core.async :refer [>! <! go go-loop chan]]
            [chord.client :refer [ws-ch]]))

(defn message-loop! [channel handler!]
  (go-loop []
    (if-let [value (<! channel)]
      (let [{:keys [message error]} value]
        (if error
          (log/error error)
          (do
            (log/info "Server said:" message) ;; TODO delete me
            (handler! message)
            (recur))))
      (console.log "Connection closed."))))

(defn connect! [url handler!]
  (let [sender-fn-channel (chan)]
    (go
      (let [{:keys [ws-channel error]} (<! (ws-ch url {:format :str}))]
        (if error
          (log/error "Failed to establish websocket connection:" error)
          (let [sender-fn #(go (>! ws-channel %))]
            (log/info "Connection established.")
            (>! sender-fn-channel sender-fn)
            (message-loop! ws-channel handler!)))))
    sender-fn-channel))

