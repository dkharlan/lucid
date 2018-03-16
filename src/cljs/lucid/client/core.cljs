(ns lucid.client.core
  (:require [clojure.string :refer [split-lines]]
            [cljs.core.async :refer [<! go]]
            [reagent.core :as r]
            [lucid.client.components :as c]
            [lucid.client.websockets :as ws]))

(def websocket-connection-endpoint "ws://localhost:8080/connect")

;; TODO (sort of) infers a newline at the end of every input... problem?
(defn append-to-buffer [buffer input]
  (->> input
    (split-lines)
    (into buffer)))

(defn init! []
  (go
    (let [buffer            (r/atom [])
          handler!          #(swap! buffer append-to-buffer %)
          sender-fn-channel (ws/connect! websocket-connection-endpoint handler!)
          sender-fn         (<! sender-fn-channel)]
      (r/render
        [c/root {:buffer buffer :send-message! sender-fn}]
        (.getElementById js/document "app")))))

(. js/document
  (addEventListener "DOMContentLoaded" init!))

