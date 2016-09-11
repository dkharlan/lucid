(ns lucid.states
  (:require [reduce-fsm :refer [defsm-inc] :as fsm]))

(defn character-exists? [[accumulator character-name]]
  (println character-name)
  (some #(= character-name %) ["Bob" "Billy" "Joe"]))

(defn- add-character-name [accumulator input current-state next-state]
  accumulator)

(defsm-inc login
  [[:awaiting-name
    [_ :guard character-exists?] -> {:action add-character-name} :derp]
   [:derp
    [_] -> :derp]]
  :dispatch :event-acc-vec)

