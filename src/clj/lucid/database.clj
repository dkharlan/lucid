(ns lucid.database
  (:import datomic.Util)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [me.raynes.fs :refer [extension]]
            [datomic.api :as d]))

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

(defn reset! [uri]
  (d/delete-database uri)
  (d/create-database uri)
  (let [schema-transaction (read-schemas "schema")
        connection (d/connect uri)]
    @(d/transact connection schema-transaction)))

