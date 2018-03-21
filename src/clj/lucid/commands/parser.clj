(ns lucid.commands.parser
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [reduce-fsm :as fsm :refer [defsm-inc]]))

;; TODO ???
(defn test-guard? [[{:keys [remaining-args] :as acc} input]]
  (log/debug "guard:" acc input)
  (> remaining-args 1))

(defn take-character [accumulator input _ _]
  (log/debug "take-character" accumulator input)
  (update-in accumulator [:characters] conj input))

(defn take-string [{:keys [characters] :as accumulator} _ _ _]
  (if (empty? characters)
    accumulator
    (-> accumulator
      (update-in [:strings] conj (string/join characters))
      (assoc-in [:characters] []))))

(defn take-string-then-character [accumulator input prev-state next-state]
  (-> accumulator
    (take-string input prev-state next-state)
    (take-character input prev-state next-state)))

(defsm-inc parser
  [[:input
    [[_ \space]]               ->                                      :consuming-spaces
    [[_ :end]]                 -> {:action take-string}                :input
    [[_ _]]                    -> {:action take-character}             :input]
   [:consuming-spaces
    [[_ :end]]                 -> {:action take-string}                :input
    [[_ \space]]               ->                                      :consuming-spaces
    [[_ _]]                    -> {:action take-string-then-character} :input]]
  :default-acc {:remaining-args 1 :strings [] :characters []}
  :dispatch :event-acc-vec)

