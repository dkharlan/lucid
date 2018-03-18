(ns lucid.client.components)

;; TODO might need this later
;; (defn scroll-to-bottom! [textarea]
;;   (if textarea
;;     (set! (.-scrollTop textarea) (.-scrollHeight textarea))))

(def color->rgb
  {:default      "#d3d3d3"
   :light-blue   "#1b4de2"
   :dark-blue    "#000066"
   :light-cyan   "#00ccff"
   :dark-cyan    "#009999"
   :light-green  "#33cc33"
   :dark-green   "#006600"
   :pink         "#ff00ff"
   :purple       "#660066"
   :light-red    "#ff0000"
   :dark-red     "#800000"
   :white        "#ffffff"
   :gray         "#6d6d6d"
   :light-yellow "#ffff00"
   :dark-yellow  "#cc9900"})

(defn text-run [{:keys [text-run]}]
  (let [text        (if (string? text-run)
                      text-run
                      (:text text-run))
        color       (if (map? text-run)
                      (:color text-run)
                      :default)
        color-value (get color->rgb color)]
    [:span {:style {:color color-value}}
     text]))

(defn line [{:keys [line]}]
  [:div {:class "line"}
   (cond
     (string? line)
     [text-run {:text-run line}]

     (coll? line)
     (map-indexed
       (fn [index part]
         [text-run {:key index :text-run part}])
       line))])

(defn output-console [{:keys [buffer]}]
  [:div {:class "content"}
   (map-indexed
     (fn [index l]
       [line {:key index :line l}])
     @buffer)])

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

