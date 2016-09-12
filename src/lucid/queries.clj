(ns lucid.queries
  (:require [lucid.database :as db]
            [datomic.api :as d]))

(defn get-connection []
  (-> db/uri (d/connect)))

(defn get-db []
  (-> db/uri (d/connect) (d/db)))

(defn does-character-exist? [character-name]
  (-> (d/q '[:find ?e
             :where [?e :character/name character-name]]
        (get-db))
    (not-empty)
    (boolean)))

(defn get-password-hash-and-salt-for-character [character-name]
  (let [hash-and-salt (-> (d/q '[:find ?password-hash-salt 
                                 :where
                                 [_ :character/name character-name]
                                 [_ :character/password-hash-and-salt ?password-salt]]
                            (get-db))
                        (first)
                        (first))]
    hash-and-salt))

(defn create-character! [name password-hash-and-salt]
  @(d/transact (get-connection)
     [{:db/id (d/tempid :db.part/user)
       :character/name name
       :character/password-hash-and-salt password-hash-and-salt}]))

