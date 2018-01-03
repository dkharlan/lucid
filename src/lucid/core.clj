(ns lucid.core
  (:gen-class)
  (:require [taoensso.timbre :as log]
            [lucid.util :as util]
            [lucid.queries :as q]
            [lucid.database :as ldb]
            [lucid.characters :as ch]
            [datomic.api :as db]))

(log/merge-config!
  {:appenders
   {:println
    {:output-fn util/logger-output-with-thread}}})

(ldb/reset! ldb/uri)
(def conn (q/get-connection))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
