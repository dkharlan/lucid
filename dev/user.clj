(ns user
  (:require [taoensso.timbre :as log]
            [figwheel-sidecar.repl-api :as f]
            [lucid.database :as ldb]
            [lucid.util :as util]
            [lucid.server.core :as srv]))

;; TODO needs to be moved somewhere global
(log/merge-config!
  {:appenders
   {:println
    {:output-fn util/logger-output-with-thread}}})

(defonce $server nil)

(defn init-lucid!
  ([]
   (init-lucid! true))
  ([reset-db?]
   (if reset-db?
     (ldb/reset-db! ldb/uri))
   (if $server
     (srv/stop! $server))
   (def $server
     (srv/make-server
       {:db-uri ldb/uri}))
   (def $db-conn (:db-connection $server))
   (srv/start! $server)))

(defn fig-start! []
  (f/start-figwheel!))

(defn fig-stop! []
  (f/stop-figwheel!))

(defn cljs-repl! []
  (f/cljs-repl))

