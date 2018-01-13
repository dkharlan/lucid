(ns lucid.database
  (:import datomic.Util)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datomic.api :as d]))

(def uri "datomic:mem://lucid")

(defn read-schema [schema-path]
  (-> schema-path (io/resource) (io/reader) (Util/readAll) (first)))

(defn reset! [uri]
  (d/delete-database uri)
  (d/create-database uri)
  (let [schema-transaction (read-schema "schema.edn")
        connection (d/connect uri)]
    @(d/transact connection schema-transaction)))

