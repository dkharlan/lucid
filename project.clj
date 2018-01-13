(defproject lucid "0.1.0-SNAPSHOT"
  :description "A flexible MUD framework written in Clojure"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [clj-time "0.14.2"]
                 [automat "0.2.4"]
                 [reduce-fsm "0.1.4"]
                 [com.datomic/datomic-pro "0.9.5656" :exclusions [com.google.guava/guava]]
                 [org.clojure/core.async "0.3.465"]
                 [org.clojure/data.codec "0.1.0"]
                 [buddy/buddy-hashers "1.0.0"]
                 [aleph "0.4.4"]
                 [com.taoensso/timbre "4.10.0"]
                 [manifold "0.1.6"]
                 [danlentz/clj-uuid "0.1.6"]]
  :main ^:skip-aot lucid.core
  :source-paths ["src/clj"]
  :target-path "target/%s"
  :repl-options {:init (do
                         (require '[alembic.still :as alembic]))}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[alembic "0.3.2"]
                                  [figwheel-sidecar "0.5.4-6"]]
                   :source-paths ["src/clj" "dev"]
                   :repl-options {:init-ns user}}
             :repl {:dependencies [[acyclic/squiggly-clojure "0.1.8"]]
                    :env {:squiggly {:checkers [:eastwood :kibit]
                                     :eastwood-options {:add-linters [:unused-locals
                                                                      :unused-fn-args
                                                                      :unused-private-vars
                                                                      :keyword-typos]}}}}}
  :plugins [[lein-environ "1.0.0"]
            [lein-figwheel "0.5.14"]])

