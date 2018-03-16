(ns lucid.client.core
  (:require [cljs.core.async :refer [<! go]]
            [reagent.core :as r]
            [lucid.client.components :as c]
            [lucid.client.websockets :as ws]))

(def websocket-connection-endpoint "ws://localhost:8080/connect")

(defn init! []
  (go
    (let [buffer            (r/atom [])
          handler!          #(swap! buffer conj %)
          sender-fn-channel (ws/connect! websocket-connection-endpoint handler!)
          sender-fn         (<! sender-fn-channel)]
      (r/render
        [c/root {:buffer buffer :send-message! sender-fn}]
        (.getElementById js/document "app")))))

(. js/document
  (addEventListener "DOMContentLoaded" init!))

