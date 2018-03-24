(ns lucid.commands.parser
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [reduce-fsm :as fsm :refer [defsm-inc]]))

(defn on-last-argument? [[{:keys [remaining-args]} _]]
  (log/debug "remaining args:" remaining-args)
  (let [res (>= 1 remaining-args)]
    (log/debug (if-not res "not") "on last arg")
    res))

(defn take-character [accumulator input _ _]
  (log/debug "take-character" accumulator input)
  (update-in accumulator [:characters] conj input))

(defn take-string [{:keys [characters] :as accumulator} _ _ _]
  (if (empty? characters)
    accumulator
    (-> accumulator
      (update-in [:strings] conj (string/join characters))
      (update-in [:remaining-args] dec)
      (assoc-in [:characters] []))))

(defn take-string-then-character [accumulator input prev-state next-state]
  (-> accumulator
    (take-string input prev-state next-state)
    (take-character input prev-state next-state)))

(defsm-inc parser
  [[:input
    [[_ \space] :guard on-last-argument?] -> {:action take-character}             :input
    [[_ \space]]                          ->                                      :consuming-spaces
    [[_ :end]]                            -> {:action take-string}                :input
    [[_ _]]                               -> {:action take-character}             :input]
   [:consuming-spaces
    [[_ :end]]                            -> {:action take-string}                :input
    [[_ \space]]                          ->                                      :consuming-spaces
    [[_ _]]                               -> {:action take-string-then-character} :input]]
  :dispatch :event-acc-vec)

(defn parse [arity message]
  (-> message
    (vec)
    (conj :end)
    (->>
      (reduce #(fsm/fsm-event %1 %2)
        (parser {:remaining-args arity :characters [] :strings []})))
    (:value)
    (select-keys [:strings :remaining-args])))

