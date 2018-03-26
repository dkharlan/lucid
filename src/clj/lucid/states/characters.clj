(ns lucid.states.characters
  (:require [lucid.states.helpers :as h]
            [lucid.characters :as chars]
            [lucid.commands.core :as cm]))

(def character-name-regex #"^[A-z]{3,}$")
(def password-regex #"^[A-Za-z\d]{8,}$")

(defn password-matches-initial? [[{{:keys [initial-password]} :login} {password :message}]]
  (= password initial-password))

(defn- add-character-name [accumulator input & _]
  (assoc-in accumulator [:login :character-name] input))

(defn add-new-character-name [accumulator {character-name :message} & _]
  (-> accumulator
    (add-character-name character-name)
    (h/queue-stream-send-to-self "Hello! I don't recognize you.  Please enter a new password.")))

(defn add-existing-character-name [accumulator {character-name :message} & _]
  (-> accumulator
    (add-character-name character-name)
    (h/queue-stream-send-to-self (str "Welcome back, " character-name ". Please enter your password."))))

(defn add-initial-password [accumulator {password :message} & _]
  (-> accumulator
    (assoc-in [:login :initial-password] password)
    (h/queue-stream-send-to-self "Please confirm your password.")))

(defn print-name-rules [accumulator & _]
  (h/queue-stream-send-to-self accumulator "Character names must be alphabetical characters only and must be at least three letters."))

(defn print-password-rules [accumulator & _]
  (h/queue-stream-send-to-self accumulator "Passwords must be alphanumeric and at least 8 characters long."))

(defn print-invalid-password [accumulator {{:keys [descriptors]} :server-info} _ _]
  (let [descriptor-id  (get-in accumulator [:login :descriptor-id])
        remote-addr    (get-in descriptors [descriptor-id :info :remote-addr])
        character-name (get-in accumulator [:login :character-name])]
    (-> accumulator
      (h/queue-stream-send-to-self "Incorrect password. Goodbye.")
      (h/queue-log-event :warn
        remote-addr "failed password authentication for" (str "\"" character-name "\"")))))

(defn log-character-in [accumulator {{:keys [descriptors]} :server-info} _ _]
  (let [descriptor-id  (get-in accumulator [:login :descriptor-id])
        character-name (get-in accumulator [:login :character-name])
        remote-addr    (get-in descriptors [descriptor-id :info :remote-addr])]
    (-> accumulator
      (h/queue-stream-send-to-self "Welcome!")
      (h/queue-log-event :info
        "Character" (str "\"" character-name "\"") "logged in from" remote-addr))))

(defn create-character [accumulator _ _ _]
  (let [{character-name :character-name password :initial-password} (:login accumulator)
         descriptor-id (get-in accumulator [:login :descriptor-id])]
    (-> accumulator
      (update-in [:login] dissoc :initial-password)
      (h/queue-stream-send-to-self (str "Thanks for creating your character, " character-name "!"))
      (h/queue-db-transaction (chars/make-character character-name password))
      (h/queue-log-event :info
        "Descriptor" descriptor-id "created character" (str "\"" character-name "\"")))))

(defn add-descriptor-id [accumulator {:keys [descriptor-id]} & _]
  (assoc-in accumulator [:login :descriptor-id] descriptor-id))

(defn print-goodbye [accumulator & _]
  (h/queue-stream-send-to-self accumulator "Goodbye."))

(defn character-exists? [[_ {character-name :message {:keys [db]} :server-info}]]
  (chars/character-exists? db character-name))

(defn password-is-valid? [[{{:keys [character-name]} :login} {password :message {:keys [db]} :server-info}]]
  (chars/password-is-valid? db character-name password))

