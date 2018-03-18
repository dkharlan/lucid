(ns lucid.client.core
  (:require [clojure.string :refer [split-lines]]
            [cljs.core.async :refer [<! go]]
            [cljs.reader :refer [read-string]]
            [reagent.core :as r]
            [lucid.client.components :as c]
            [lucid.client.websockets :as ws]))

(def websocket-connection-endpoint "ws://localhost:8080/connect")

(defn init! []
  (go
    (let [buffer            (r/atom [])
          handler!          #(swap! buffer conj (read-string %))
          close-handler!    #(swap! buffer conj [{:color :white :text "Connection closed."}])
          sender-fn-channel (ws/connect! websocket-connection-endpoint handler! close-handler!)
          sender-fn         (<! sender-fn-channel)]
      (r/render
        [c/root {:buffer buffer :send-message! sender-fn}]
        (.getElementById js/document "app")))))

(. js/document
  (addEventListener "DOMContentLoaded" init!))

