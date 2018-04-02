(ns lucid.states.core
  (:require [reduce-fsm :refer [defsm-inc]]
            [lucid.states.characters :as cs]))

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
;; there are currently three types of side effects implemented:
;;    :db for datomic => a vector of Datomic transactions
;;    :stream for output to streams => vector of {:destination <stream uuid> :message <string>}
;;    :log for logging => vector of {:level level :messages messages}
;;
;; see lucid.states.helpers for an API to queue these
;;

;; TODO because macros... see below
(def character-name-regex cs/character-name-regex)
(def password-regex cs/password-regex)

;; TODO find a way to macroexpand this so that most of it can go in lucid.states.characters
(defsm-inc game
   [[:initial
     [[_ {:descriptor-id _}]] -> {:action cs/add-descriptor-id} :awaiting-name
     [_]                      ->                                :initial]
    [:awaiting-name
     [[_ {:server-info _ :event-data character-name-regex}] :guard cs/character-exists?] -> {:action cs/add-existing-character-name} :awaiting-password
     [[_ {:server-info _ :event-data character-name-regex}]]                             -> {:action cs/add-new-character-name}      :awaiting-initial-password
     [_]                                                                                 -> {:action cs/print-name-rules}            :awaiting-name]
    [:awaiting-password
     [_ :guard cs/password-is-valid?] -> {:action cs/log-character-in}       :logged-in
     [_]                              -> {:action cs/print-invalid-password} :zombie]
    [:awaiting-initial-password
     [[_ {:server-info _ :event-data password-regex}]] -> {:action cs/add-initial-password} :awaiting-password-confirmation
     [_]                                               -> {:action cs/print-password-rules} :awaiting-initial-password]
    [:awaiting-password-confirmation
     [[_ {:server-info _ :event-data password-regex}] :guard cs/password-matches-initial?] -> {:action cs/create-character} :logged-in
     [_]                                                                                   -> {:action cs/print-goodbye}    :zombie]
    [:logged-in
     [[_ {:server-info _ :event-data "quit"}]] -> {:action cs/print-goodbye}  :zombie ;; TODO replace me with a command
     [_]                                    -> {:action cs/handle-command} :logged-in]
    [:zombie
     [_] -> :zombie]]
   :default-acc {:side-effects {:stream [] :db [] :log []}}
   :dispatch :event-acc-vec)
