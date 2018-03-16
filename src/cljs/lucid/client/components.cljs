(ns lucid.client.components)

(defn scroll-to-bottom! [textarea]
  (if textarea
    (set! (.-scrollTop textarea) (.-scrollHeight textarea))))

(defn output-console [{:keys [buffer]}]
  [:div {:class "content"}
   [:textarea
    {:class "output-console zero-fill"
     :read-only true
     :ref scroll-to-bottom!
     :value (apply str @buffer)}]]) ;; TODO doubt this is performant

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

