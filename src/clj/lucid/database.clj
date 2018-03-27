(ns lucid.database
  (:import datomic.Util)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [me.raynes.fs :refer [extension]]
            [datomic.api :as d]
            [taoensso.timbre :as log]
            [datomic.api :as db]))

(def uri "datomic:mem://lucid")

(defn read-schema [schema-file]
  (-> schema-file
    (io/reader)
    (Util/readAll)
    (first)))

(defn read-schemas [schema-directory-path]
  (->> schema-directory-path
    (io/resource)
    (io/file)
    (file-seq)
    (filter #(= ".edn" (extension %)))
    (mapcat read-schema)))

(defn ensure-defaults [txns]
  (letfn [(does-not-have? [attrib value description]
            (let [res (not-any? #(= (get % attrib) value) txns)]
              (if res
                (log/info
                  (str "Didn't find a " description "; creating one")))
              res))]
    (cond-> (vec txns)
      (does-not-have? :room/tag :character/starting-location
        "room with :room/tag equal to :character/starting-location")
      (conj
        {:room/name        "Default Starting Room"
         :room/tag         :character/starting-location
         :room/description "Lucid requires a room with :room/tag equal to :character/starting-location; since you didn't provide one, a default has been created."}))))

;; TODO should change this to migrate! or something like that
(defn reset-db! [uri]
  (d/delete-database uri)
  (d/create-database uri)
  (let [schema-transactions (read-schemas "db/schema")
        data-transactions   (->> "db/data"
                              (read-schemas)
                              (ensure-defaults)
                              (map #(assoc % :db/id (d/tempid :db.part/user))))
        connection          (d/connect uri)]
    @(d/transact connection schema-transactions)
    @(d/transact connection data-transactions)))

(defn speculate [db txns]
  (:db-after
   (d/with db txns)))

