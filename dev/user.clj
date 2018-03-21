(ns user
  (:require [taoensso.timbre :as log]
            [figwheel-sidecar.repl-api :as f]
            [lucid.database :as ldb]
            [lucid.util :as util]))

(use 'clojure.repl)

(log/merge-config!
  {:appenders
   {:println
    {:output-fn util/logger-output-with-thread}}})

;; TODO this is only for datomic's in-memory transactor
(ldb/reset! ldb/uri)

(defn fig-start! []
  (f/start-figwheel!))

(defn fig-stop! []
  (f/stop-figwheel!))

(defn cljs-repl! []
  (f/cljs-repl))

