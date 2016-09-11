(ns lucid.states
  (:require [reduce-fsm :refer [defsm-inc] :as fsm]))

(def test-identities {"Bob" "foobar"
                      "Billy" "12345"
                      "Joe" "changeme"})

(defn character-exists? [[_ character-name]]
  (and
    (string? character-name)
    (some #(= character-name %) (keys test-identities))))

(defn password-is-valid? [[{{:keys [character-name]} :login} password]]
  (= password (get test-identities character-name)))

(defn password-matches-initial? [[{{:keys [initial-password]} :login :as accum} password]]
  (= password initial-password))

(defn- add-character-name [accumulator input & _]
  (assoc-in accumulator [:login :character-name] input))

(defn- add-initial-password [accumulator input & _]
  (assoc-in accumulator [:login :initial-password] input))

(def character-name-regex #"^[A-z]{3,}$")
(def password-regex #"^[A-Za-z\d]{8,}$")

(defn print-name-rules [accumulator & _]
  (println "Character names must be alphabetical characters only and must be at least three letters.")
  accumulator)

(defn print-password-rules [accumulator & _]
  (println "Passwords must be alphanumeric and at least 8 characters long.")
  accumulator)

(defn print-invalid-password [accumulator & _]
  (println "Incorrect password. Goodbye.")
  accumulator)

(defn print-login-message [accumulator & _]
  (println "Welcome!")
  accumulator)

(defn log-character-in [accumulator & _]
  (println "Thanks for creating your character," (str (get-in accumulator [:login :character-name]) "!"))
  (update-in accumulator [:login] dissoc :initial-password))

(defn print-goodbye [accumulator & _]
  (println "Goodbye."))

(defsm-inc login
  [[:awaiting-name
    [[_ character-name-regex] :guard character-exists?] -> {:action add-character-name} :awaiting-password
    [[_ character-name-regex] :guard #(not (character-exists? %))] -> {:action add-character-name} :awaiting-initial-password
    [_] -> {:action print-name-rules} :awaiting-name]
   [:awaiting-password
    [_ :guard password-is-valid?] -> {:action print-login-message} :logged-in
    [_] -> {:action print-invalid-password} :zombie]
   [:awaiting-initial-password
    [[_ password-regex]] -> {:action add-initial-password} :awaiting-password-confirmation
    [_] -> {:action print-password-rules} :awaiting-initial-password]
   [:awaiting-password-confirmation
    [[_ password-regex] :guard password-matches-initial?] -> {:action log-character-in} :logged-in
    [_] -> {:action print-goodbye} :zombie]
   [:logged-in
    [[_ "quit"]] -> {:action print-goodbye} :zombie
    [_] -> :logged-in]
   [:zombie
    [_] -> :zombie]]
  :dispatch :event-acc-vec)

