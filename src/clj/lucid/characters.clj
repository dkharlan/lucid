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

(defn make-character [character-name password]
  {:db/id (db/tempid :db.part/user)
   :character/name                   character-name
   :character/password-hash-and-salt (hash/derive password)
   :character/body                   {:body/location [:room/tag :character/starting-location]}})

;; example queries
(comment

  ;; bodies with logged in characters
  (db/q '[:find ?b
          :in $ [?cn ...]
          :where [?b :body/location]
          [?c :character/body ?b]
          [?c :character/name ?cn]]
    (db/db (:db-connection $server))
    (map #(get-in % [:value :login :character-name]) (vals @(:states $server))))

  ;; bodies with logged in characters in the starting room
  (db/q '[:find ?b
          :in $ [?cn ...]
          :where [?b :body/location [:room/tag :character/starting-location]]
          [?c :character/body ?b]
          [?c :character/name ?cn]]
    (db/db (:db-connection $server))
    (map #(get-in % [:value :login :character-name]) (vals @(:states $server))))

  ;; bodies with logged in characters in a given room
  (db/q '[:find ?b
          :in $ [?cn ...]
          :where [?b :body/location 17592186045418]
          [?c :character/body ?b]
          [?c :character/name ?cn]]
    (db/db (:db-connection $server))
    (map #(get-in % [:value :login :character-name]) (vals @(:states $server))))

  ;; bodies in the same room as a given character
  (db/q '[:find ?b
          :in $ ?cn
          :where [?c :character/name ?cn]
          [?c :character/body ?cb]
          [?cb :body/location ?r]
          [?b :body/location ?r]]
    (db/db (:db-connection $server))
    "Bob")

  ;; bodies in the same room as a given character, excluding the given character's
  (db/q '[:find ?b
          :in $ ?cn
          :where [?c :character/name ?cn]
          [?c :character/body ?cb]
          [?cb :body/location ?r]
          [?b :body/location ?r]
          [(!= ?b ?cb)]]
    (db/db (:db-connection $server))
    "Bob")

  ;; logged in bodies in the same room as a given character, excluding the given character's
  (db/q '[:find ?b
          :in $ ?cn [?logged-in-cn ...]
          :where [?c :character/name ?cn]
          [?c :character/body ?cb]
          [?cb :body/location ?r]
          [?b :body/location ?r]
          [?logged-in-c :character/body ?b]
          [?logged-in-c :character/name ?logged-in-cn]
          [(!= ?b ?cb)]]
    (db/db (:db-connection $server))
    "Bob"
    (map #(get-in % [:value :login :character-name]) (vals @(:states $server))))

  ;; logged-in characters in the same room as a given character (excluding the given character's)
  (db/q '[:find (pull ?logged-in-c [*])
          :in $ ?cn [?logged-in-cn ...]
          :where [?c :character/name ?cn]
                 [?c :character/body ?cb]
                 [?cb :body/location ?r]
                 [?b :body/location ?r]
                 [?logged-in-c :character/body ?b]
                 [?logged-in-c :character/name ?logged-in-cn]
                 [(!= ?b ?cb)]]
    (db/db (:db-connection $server))
    "Bob"
    (map #(get-in % [:value :login :character-name]) (vals @(:states $server))))

  ;; logged-in character names and bodies in the same room as a given character (excluding the given character's)
  (db/q '[:find (pull ?logged-in-c [:character/name :character/body])
          :in $ ?cn [?logged-in-cn ...]
          :where [?c :character/name ?cn]
                 [?c :character/body ?cb]
                 [?cb :body/location ?r]
                 [?b :body/location ?r]
                 [?logged-in-c :character/body ?b]
                 [?logged-in-c :character/name ?logged-in-cn]
                 [(!= ?b ?cb)]]
    (db/db (:db-connection $server))
    "Bob"
    (map #(get-in % [:value :login :character-name]) (vals @(:states $server))))
  
  )

