(ns lucid.client.core
  (:require [cljs.core.async :refer [>! <! put! close! go go-loop chan]]
            [taoensso.timbre :as log] 
            [chord.client :refer [ws-ch]]
            [reagent.core :as r]))

;; TODO make a simple console and command entry control

(def websocket-connection-endpoint "ws://localhost:8080/connect")

(defn message-loop! [channel]
  (go-loop []
    (if-let [value (<! channel)]
      (let [{:keys [message error]} value]
        (if error
          (log/error error)
          (do
            (log/info "Server said:" message)
            (recur))))
      (console.log "Connection closed."))))

(defn connect! [url]
  (let [sender-fn-channel (chan)]
    (go
      (let [{:keys [ws-channel error]} (<! (ws-ch url {:format :str}))]
        (if error
          (log/error "Failed to establish websocket connection:" error)
          (let [sender-fn #(go (>! ws-channel %))]
            (log/info "Connection established.")
            (>! sender-fn-channel sender-fn)
            (message-loop! ws-channel)))))
    sender-fn-channel))

(defn output-console [{:keys [buffer]}]
  [:div
   (map-indexed
     (fn [index line]
       ^{:key index} [:p line])
     @buffer)])

(defn input-line [{:keys [send-message!]}]
  (letfn [(enter-handler [event]
            (if (= (-> event .-keyCode) 13)
              (let [message (-> event .-target .-value)]
                (send-message! message))))
          (send-click-handler [event]
            (let [message (-> event .-target .-parentNode (.querySelector "input") .-value)]
              (send-message! message)))]
    [:div
     [:input {:type "text" :on-key-down enter-handler}]
     [:button {:on-click send-click-handler} "Send"]]))

(defn root [{:keys [buffer send-message!] :as props}]
  [:div
   [output-console {:buffer buffer}]
   [input-line {:send-message! send-message!}]])

(.addEventListener js/document "DOMContentLoaded"
  (fn [_]
    (go
      (let [sender-fn (<! (connect! websocket-connection-endpoint))
            buffer    (r/atom [])]
        (r/render
          [root {:buffer buffer :send-message! sender-fn}]
          (.getElementById js/document "app"))))))

