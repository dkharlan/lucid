(ns lucid.queries
  (:require [lucid.database :as db]
            [datomic.api :as d]))

(defn get-connection []
  (-> db/uri (d/connect)))

(defn get-db []
  (-> (get-connection) (d/db)))

(defn character-exists? [character-name]
  (-> (d/q '[:find ?e
             :in $ ?character-name
             :where [?e :character/name ?character-name]]
        (get-db)
        character-name)
    (not-empty)
    (boolean)))

(defn get-character [character-name]
  (-> '[:find (pull ?e [*])
        :in $ ?character-name
        :where [?e :character/name ?character-name]]
    (d/q (get-db) character-name)
    (first)
    (first)))

(defn create-character! [character-name password-hash-and-salt]
  @(d/transact (get-connection)
     [{:db/id (d/tempid :db.part/user)
       :character/name character-name
       :character/password-hash-and-salt password-hash-and-salt}]))

