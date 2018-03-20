(ns lucid.client.components
  (:require [taoensso.timbre :as log]))

;; TODO might need this later
;; (defn scroll-to-bottom! [textarea]
;;   (if textarea
;;     (set! (.-scrollTop textarea) (.-scrollHeight textarea))))

(defn text-run [{:keys [text-run]}]
  (let [text        (if (string? text-run)
                      text-run
                      (:text text-run))
        color       (if (map? text-run)
                      (:color text-run)
                      :default)
        color-class (str "text-run--" (name color))]
    [:span {:class ["text-run" color-class]}
     text]))

(defn line [{:keys [line]}]
  [:div {:class "line"}
   (cond
     (or (string? line) (map? line)) 
     [text-run {:text-run line}]

     (coll? line)
     (map-indexed
       (fn [index part]
         [text-run {:key index :text-run part}])
       line))])

(defn output-console [{:keys [buffer]}]
  [:div {:class "console"}
   [:div {:class "content"}
    (map-indexed
      (fn [index l]
        [line {:key index :line l}])
      @buffer)]])

(defn input-line [{:keys [send-message!]}]
  (letfn [(enter-handler [event]
            (if (= (-> event .-keyCode) 13)
              (let [input-box (.-target event)
                    message   (.-value input-box)]
                (send-message! message)
                (.select input-box))))]
    [:div {:class "footer"}
     [:input {:class ["input-box" "zero-fill"]
              :type "text"
              :on-key-down enter-handler}]]))

(defn root [{:keys [buffer send-message!] :as props}]
  [:div {:class "container"}
   [output-console {:buffer buffer}]
   [input-line {:send-message! send-message!}]])

