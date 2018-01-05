(ns lucid.states
  (:require [reduce-fsm :refer [defsm-inc] :as fsm]
            [lucid.characters :as chars]))

(def character-name-regex #"^[A-z]{3,}$")
(def password-regex #"^[A-Za-z\d]{8,}$")

;;
;; states can keep anything in their accumulator, but the :side-effects entry is treated
;; specially. state actions can use that to accumulate side effects to be applied
;; by the fsm caller. actions or state guards should not depend on :side-effects, as
;; callers are expected to dissoc it before sending additional inputs to the fsm.
;;
;; the value of :side-effects is a map with entries corresponding to distinct state
;; containers. e.g., :db for a database, :server for transient server state, etc.
;;
;; for lucid probably 3 will be needed:
;;    :db for datomic
;;    :server for session -> character association, session termination, etc.
;;    :output for output to streams
;;

(defn password-matches-initial? [[{{:keys [initial-password]} :login :as accum} password]]
  (= password initial-password))

;;; actions

(defn- add-character-name [accumulator input & _]
  (assoc-in accumulator [:login :character-name] input))

(defn- send-to-self [accumulator message]
  (update-in accumulator [:side-effects :stream] conj message))

(defn- queue-txn [accumulator txn]
  (update-in accumulator [:side-effects :db] conj txn))

(defn add-new-character-name [accumulator input & _]
  (-> accumulator
    (add-character-name input)
    (send-to-self "Hello! I don't recognize you.  Please enter a new password.")))

(defn add-existing-character-name [accumulator input & _]
  (-> accumulator
    (add-character-name input)
    (send-to-self (str "Welcome back, " input ". Please enter your password."))))

(defn add-initial-password [accumulator input & _]
  (-> accumulator
    (assoc-in [:login :initial-password] input)
    (send-to-self "Please confirm your password.")))

(defn print-name-rules [accumulator & _]
  (send-to-self accumulator "Character names must be alphabetical characters only and must be at least three letters."))

(defn print-password-rules [accumulator & _]
  (send-to-self accumulator "Passwords must be alphanumeric and at least 8 characters long."))

(defn print-invalid-password [accumulator & _]
  (send-to-self accumulator "Incorrect password. Goodbye."))

(defn print-login-message [accumulator & _]
  (send-to-self accumulator "Welcome!"))

(defn log-character-in [accumulator & _]
  (let [{character-name :character-name password :initial-password} (:login accumulator)]
    (-> accumulator
      (update-in [:login] dissoc :initial-password)
      (send-to-self (str "Thanks for creating your character, " character-name "!"))
      (queue-txn (chars/create-character character-name password)))))

(defn print-goodbye [accumulator & _]
  (send-to-self accumulator "Goodbye."))

(defsm-inc game
  [[:initial
    [[_ :telnet]]   -> :awaiting-name
    [[_ :websocket] -> :logged-in]
    [_]             -> :initial]
   [:awaiting-name
    [[_ character-name-regex] :guard chars/character-exists?] -> {:action add-existing-character-name} :awaiting-password
    [[_ character-name-regex]] -> {:action add-new-character-name} :awaiting-initial-password
    [_] -> {:action print-name-rules} :awaiting-name]
   [:awaiting-password
    [_ :guard chars/password-is-valid?] -> {:action print-login-message} :logged-in
    [_] -> {:action print-invalid-password} :zombie]
   [:awaiting-initial-password
 6   [[_ password-regex]] -> {:action add-initial-password} :awaiting-password-confirmation
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

