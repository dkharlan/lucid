(ns lucid.states
  (:require [reduce-fsm :refer [defsm-inc] :as fsm]
            [lucid.characters :as chars]
            [taoensso.timbre :as log]))

;;
;; states can keep anything in their accumulator, but the :side-effects entry is treated
;; specially. state actions can use that to accumulate side effects to be applied
;; by the fsm caller. actions or state guards should not depend on :side-effects, as
;; callers are expected to dissoc it before sending additional inputs to the fsm.
;; see implementation of lucid.server.core/update! for an (the) example
;;
;; the value of :side-effects is a map with entries corresponding to distinct state
;; containers. e.g., :db for a database, :stream for stream output, etc.
;;
;; there are currently two types of side effects implemented:
;;    :db for datomic => a vector of Datomic transactions
;;    :stream for output to streams => {:destination <stream uuid> :message <string>}
;;
;; i can see a couple others being useful at some point:
;;    :server for session->character association, session termination, etc.
;;    :log for logging that should be applied by the update thread along with other effects
;;
;; TODO will need to think about how to organize everything below
;;

(def character-name-regex #"^[A-z]{3,}$")
(def password-regex #"^[A-Za-z\d]{8,}$")

(defn password-matches-initial? [[{{:keys [initial-password]} :login} {password :message}]]
  (= password initial-password))

;;; actions

(defn- add-character-name [accumulator input & _]
  (assoc-in accumulator [:login :character-name] input))

(defn- send-to-desc [accumulator desc message]
  (update-in accumulator [:side-effects :stream] conj {:destination desc :message message}))

(defn- sendln-to-desc [accumulator desc message]
  (send-to-desc accumulator desc (str message "\n")))

(defn- send-to-self [accumulator message]
  (let [self (get-in accumulator [:login :descriptor-id])]
    (send-to-desc accumulator self message)))

(defn- log [accumulator level & messages]
  (update-in accumulator [:side-effects :log] conj {:level level :messages messages}))

(defn- sendln-to-self [accumulator message]
  (send-to-self accumulator (str message "\n")))

(defn- queue-txn [accumulator txn]
  (update-in accumulator [:side-effects :db] conj txn))

(defn add-new-character-name [accumulator {character-name :message} & _]
  (-> accumulator
    (add-character-name character-name)
    (sendln-to-self "Hello! I don't recognize you.  Please enter a new password.")))

(defn add-existing-character-name [accumulator {character-name :message} & _]
  (-> accumulator
    (add-character-name character-name)
    (sendln-to-self (str "Welcome back, " character-name ". Please enter your password."))))

(defn add-initial-password [accumulator {password :message} & _]
  (-> accumulator
    (assoc-in [:login :initial-password] password)
    (sendln-to-self "Please confirm your password.")))

(defn print-name-rules [accumulator & _]
  (sendln-to-self accumulator "Character names must be alphabetical characters only and must be at least three letters."))

(defn print-password-rules [accumulator & _]
  (sendln-to-self accumulator "Passwords must be alphanumeric and at least 8 characters long."))

(defn print-invalid-password [accumulator {{:keys [descriptors]} :server-info} _ _]
  (let [descriptor-id  (get-in accumulator [:login :descriptor-id])
        remote-addr    (get-in descriptors [descriptor-id :info :remote-addr])
        character-name (get-in accumulator [:login :character-name])]
    (-> accumulator
      (sendln-to-self "Incorrect password. Goodbye.")
      (log :warn remote-addr "failed password authentication for" (str "\"" character-name "\"")))))

(defn log-character-in [accumulator {{:keys [descriptors]} :server-info} _ _]
  (let [descriptor-id  (get-in accumulator [:login :descriptor-id])
        character-name (get-in accumulator [:login :character-name])
        remote-addr    (get-in descriptors [descriptor-id :info :remote-addr])]
    (-> accumulator
      (sendln-to-self "Welcome!")
      (log :info "Character" (str "\"" character-name "\"") "logged in from" remote-addr))))

(defn create-character [accumulator _ _ _]
  (let [{character-name :character-name password :initial-password} (:login accumulator)
         descriptor-id (get-in accumulator [:login :descriptor-id])]
    (-> accumulator
      (update-in [:login] dissoc :initial-password)
      (sendln-to-self (str "Thanks for creating your character, " character-name "!"))
      (queue-txn (chars/create-character character-name password))
      (log :info "Descriptor" descriptor-id "created character" (str "\"" character-name "\"")))))

(defn add-descriptor-id [accumulator {:keys [descriptor-id]} & _]
  (assoc-in accumulator [:login :descriptor-id] descriptor-id))

(defn log-in-websocket-character [accumulator {:keys [character-name] :as input-params} & _]
  (-> accumulator
    (assoc-in [:login :character-name] character-name)
    (add-descriptor-id input-params)))

(defn print-goodbye [accumulator & _]
  (sendln-to-self accumulator "Goodbye."))

(defn echo-message [accumulator {:keys [server-info message]} & _]
  (let [{:keys [descriptors states]} server-info
        source-descriptor-id         (get-in accumulator [:login :descriptor-id])
        source-name                  (or
                                        (get-in states [source-descriptor-id :value :login :character-name])
                                        source-descriptor-id)]
    (update-in accumulator [:side-effects :stream] concat
      (for [destination-descriptor-id (keys descriptors)]
        {:destination destination-descriptor-id 
         :message (str
                    (if (= destination-descriptor-id source-descriptor-id) "You" source-name)
                    " said \""
                    message
                    "\"\n")}))))

(defn character-exists? [[_ {character-name :message}]]
  (chars/character-exists? character-name))

(defn password-is-valid? [[{{:keys [character-name]} :login} {password :message}]]
  (chars/password-is-valid? character-name password))

;; The first value should be one of:
;;    :telnet             for a telnet connection
;;    [:websocket name]   for a websocket connection for name
;; Subsequent values are input lines from the player
(defsm-inc game
  [[:initial
    [[_ {:type :telnet :descriptor-id _}]]                      -> {:action add-descriptor-id} :awaiting-name
    [[_ {:type :websocket :descriptor-id _ :character-name _}]] -> {:action log-in-websocket-character} :logged-in
    [_]                                                         -> :initial]
   [:awaiting-name
    [[_ {:server-info _ :message character-name-regex}] :guard character-exists?] -> {:action add-existing-character-name} :awaiting-password
    [[_ {:server-info _ :message character-name-regex}]]                          -> {:action add-new-character-name} :awaiting-initial-password
    [_]                                                                           -> {:action print-name-rules} :awaiting-name]
   [:awaiting-password
    [_ :guard password-is-valid?] -> {:action log-character-in} :logged-in
    [_]                           -> {:action print-invalid-password} :zombie]
   [:awaiting-initial-password
    [[_ {:server-info _ :message password-regex}]] -> {:action add-initial-password} :awaiting-password-confirmation
    [_]                                            -> {:action print-password-rules} :awaiting-initial-password]
   [:awaiting-password-confirmation
    [[_ {:server-info _ :message password-regex}] :guard password-matches-initial?] -> {:action create-character} :logged-in
    [_]                                                                             -> {:action print-goodbye} :zombie]
   [:logged-in
    [[_ {:server-info _ :message "quit"}]] -> {:action print-goodbye} :zombie
    [_]                                    -> {:action echo-message} :logged-in]
   [:zombie
    [_] -> :zombie]]
  :default-acc {:side-effects {:stream [] :db []}}
  :dispatch :event-acc-vec)
