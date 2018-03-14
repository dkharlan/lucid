(ns lucid.client.core
  (:require [cljs.core.async :refer [>! <! put! close! go go-loop chan]]
            [taoensso.timbre :as log] 
            [chord.client :refer [ws-ch]]
            [reagent.core :as r]))

;; TODO make a simple console and command entry control

(def websocket-connection-endpoint "ws://localhost:8080/connect")

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

(defn scroll-to-bottom! [textarea]
  (if textarea
    (set! (.-scrollTop textarea) (.-scrollHeight textarea))))

(defn output-console [{:keys [buffer]}]
  [:div {:class "content"}
   [:textarea
    {:class "output-console zero-fill"
     :read-only true
     :ref scroll-to-bottom!
     :value (apply str (interpose "\n" @buffer))}]]) ;; TODO doubt this is performant

(defn input-line [{:keys [send-message!]}]
  (letfn [(enter-handler [event]
            (if (= (-> event .-keyCode) 13)
              (let [message (-> event .-target .-value)]
                (send-message! message))))]
    [:div {:class "footer"}
     [:input {:class "input-box zero-fill"
              :type "text"
              :on-key-down enter-handler}]]))

(defn root [{:keys [buffer send-message!] :as props}]
  [:div {:class "container"}
   [output-console {:buffer buffer}]
   [input-line {:send-message! send-message!}]])

(.addEventListener js/document "DOMContentLoaded"
  (fn [_]
    (go
      (let [buffer            (r/atom [])
            handler!          #(swap! buffer conj %)
            sender-fn-channel (connect! websocket-connection-endpoint handler!)
            sender-fn         (<! sender-fn-channel)]
        (r/render
          [root {:buffer buffer :send-message! sender-fn}]
          (.getElementById js/document "app"))))))

