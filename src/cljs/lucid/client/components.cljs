(ns lucid.client.components
  (:require [taoensso.timbre :as log]
            [lucid.client.helpers :refer [scrolled-to-bottom? scroll-to-bottom!]]
            [reagent.core :as r]))

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
  (let [!scrolled-to-bottom? (r/atom true)]
    (fn []
      [:div {:class     "console"
             :ref       (fn [console]
                          (when (and console @!scrolled-to-bottom?)
                            (scroll-to-bottom! console)))
             :on-scroll (fn [event]
                          (let [console (.-target event)
                                scrolled-to-bottom? (scrolled-to-bottom? console)]
                            (if (not= scrolled-to-bottom? @!scrolled-to-bottom?) 
                              (reset! !scrolled-to-bottom? scrolled-to-bottom?))))}
       [:div {:class "content"}
        (map-indexed
          (fn [index l]
            [line {:key index :line l}])
          @buffer)]])))

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
              :autoFocus true
              :on-key-down enter-handler}]]))

(defn root [{:keys [buffer send-message!] :as props}]
  [:div {:class "container"}
   [output-console {:buffer buffer}]
   [input-line {:send-message! send-message!}]])

