(ns lucid.client.components)

;; TODO might need this later
;; (defn scroll-to-bottom! [textarea]
;;   (if textarea
;;     (set! (.-scrollTop textarea) (.-scrollHeight textarea))))

(defn line->repr [line]
  (if (coll? line)
    (throw (js/Error. "Not implemented yet"))
    [:span line]))

(defn output-console [{:keys [buffer]}]
  [:div {:class "content"}
   (map-indexed
     (fn [index line]
       ^{:key index}
       [:div {:class "line"}
        (line->repr line)])
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

