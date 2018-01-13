(ns lucid.core
  (:gen-class)
  (:require [taoensso.timbre :as log]
            [lucid.util :as util]
            [lucid.queries :as q]
            [lucid.database :as ldb]
            [lucid.characters :as ch]
            [datomic.api :as db]))

;; TODO move this to -main
;; (log/merge-config!
;;   {:appenders
;;    {:println
;;     {:output-fn util/logger-output-with-thread}}})

(defn -main
  [& _]
  (throw
    (RuntimeException. "Starting Lucid as a JAR has not yet been implemented.")))

