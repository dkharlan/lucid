(ns lucid.states
  (:require [reduce-fsm :refer [defsm-inc] :as fsm]))

(defn character-exists? [[_ character-name]]
  (and
    (string? character-name)
    (some #(= character-name %) ["Bob" "Billy" "Joe"])))

(defn password-is-valid? [[{{:keys [character-name]} :login} password]]
  (let [name->password {"Bob" "foobar"
                        "Billy" "12345"
                        "Joe" "changeme"}]
    (= password (get name->password character-name))))

(defn- add-character-name [accumulator input current-state next-state]
  (assoc-in accumulator [:login :character-name] input))

(defsm-inc login
  [[:awaiting-name
    [[_ #"^[A-z]+$"] :guard character-exists?] -> {:action add-character-name} :awaiting-password]
   [:awaiting-password
    [_ :guard password-is-valid?] -> :logged-in
    [_ :guard #(not (password-is-valid? %))] -> :zombie]
   [:logged-in
    [[_ "quit"]] -> :zombie
    [_] -> :logged-in]
   [:zombie
    [_] -> :zombie]]
  :dispatch :event-acc-vec)

