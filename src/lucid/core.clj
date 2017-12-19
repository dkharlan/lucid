(ns lucid.core
  (:gen-class)
  (:require [taoensso.timbre :as log]
            [lucid.util :as util]))

(log/merge-config!
  {:appenders
   {:println
    {:output-fn util/logger-output-with-thread}}})

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
