(ns lucid.server.colors
  (:require [clojure.string :as string]
            [reduce-fsm :refer [defsm-inc] :as fsm]))

;; TODO needs to support http as well

(def color-code-start \$)

(def color-codes
  {;; dark red
   \r {:tcp  "\033[31m"
       :http :dark-red}

   ;; light red
   \R {:tcp  "\033[31;1m"
       :http :light-red} 

   ;; dark green
   \g {:tcp  "\033[32m"
       :http :dark-green}

   ;; light green
   \G {:tcp  "\033[32;1m"
       :http :light-green} 

   ;; dark yellow       
   \y {:tcp  "\033[33m"
       :http :dark-yellow}

   ;; light yellow
   \Y {:tcp  "\033[33;1m"
       :http :light-yellow} 

   ;; dark blue
   \b {:tcp  "\033[34m"
       :http :dark-blue}

   ;; light blue
   \B {:tcp  "\033[34;1m"
       :http :light-blue} 

   ;; purple
   \p {:tcp  "\033[35m"
       :http :purple}

   ;; pink
   \P {:tcp  "\033[35;1m"
       :http :pink}

   ;; dark cyan
   \c {:tcp  "\033[36m"
       :http :dark-cyan}

   ;; light cyan
   \C {:tcp  "\033[36;1m"
       :http :light-cyan}

   ;; gray
   \w {:tcp  "\033[37m"
       :http :gray}

   ;; white
   \W {:tcp  "\033[37;1m"
       :http :white}

   ;; reset
   \! {:tcp  "\033[0m"
       :http nil}

   ;; identity
   color-code-start
   {:tcp  (str color-code-start)
    :http :identity}})

(defn escape-color-codes [message]
  (string/join
    (loop [input-chars  message
           output-chars []]
      (if (empty? input-chars)
        output-chars
        (let [[next & others]   input-chars
              escape?           (= next color-code-start)
              next-input-chars  (if escape?
                                  (rest others)
                                  others)
              next-output-chars (if escape?
                                  (if-let [escape-sequence (->> others
                                                             (first)
                                                             (conj '(:tcp))
                                                             (get-in color-codes))]
                                    (into output-chars escape-sequence)
                                    output-chars)
                                  (conj output-chars next))]
          (recur next-input-chars next-output-chars))))))

(defn take-char [value input _ _]
  (update-in value [:chars] conj input))

(defn take-string [{escape :escape cs :chars :as value} _ _ _]
  (-> value
    (assoc-in [:chars] [])
    (update-in [:strs] conj (cond-> (string/join cs)
                              escape (#(do {:color escape :text %}))))))

(defn take-line [value input prev-state next-state]
  (let [{:keys [strs] :as value} (take-string value input prev-state next-state)]
    (-> value
      (assoc-in [:strs] [])
      (update-in [:lines] conj
        (if (= (count strs) 1)
          (first strs)
          strs)))))

(defn set-escape-type [{cs :chars :as value} input prev-state next-state]
  (cond-> value
    (not-empty cs) (take-string input prev-state next-state)
    true           (assoc :escape
                     (get-in color-codes [input :http]))))

(fsm/defsm-inc test-colors
  [[:input
    :end     -> {:action take-line}       :input
    \$       ->                           :escaping
    \newline -> {:action take-line}       :input
    _        -> {:action take-char}       :input]
   [:escaping
    _        -> {:action set-escape-type} :input]])

