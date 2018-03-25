(ns lucid.characters
  (:require [buddy.hashers :as hash]
            [taoensso.timbre :as log]
            [datomic.api :as db]))

(defn character-exists? [db character-name]
  (and
    (string? character-name)
    (not-empty
      (db/q '[:find ?name
              :in $ ?name
              :where [_ :character/name ?name]]
        db
        character-name))))

(defn password-is-valid? [db character-name offered-password]
  (let [password-hash-and-salt
        (-> '[:find ?hash-and-salt
              :in $ ?name
              :where [?ch :character/name ?name]
                     [?ch :character/password-hash-and-salt ?hash-and-salt]]
          (db/q db character-name)
          (first)
          (first))]
    (hash/check offered-password password-hash-and-salt)))

(defn create-character [character-name password]
  {:db/id                            (db/tempid :db.part/user)
   :character/name                   character-name
   :character/password-hash-and-salt (hash/derive password)})

