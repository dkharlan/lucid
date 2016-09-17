(defproject lucid "0.1.0-SNAPSHOT"
  :description "A flexible MUD framework written in Clojure"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.12.0"]
                 [reduce-fsm "0.1.4"]
                 [com.datomic/datomic-free "0.9.5394"]
                 [org.clojure/data.codec "0.1.0"]
                 [crypto-random "1.2.0"]
                 [buddy/buddy-hashers "1.0.0"]]
  :main ^:skip-aot lucid.core
  :target-path "target/%s"
  :repl-options {:init (do
                         (require '[alembic.still :as alembic]))}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[alembic "0.3.2"]]}
             :repl {:dependencies [[acyclic/squiggly-clojure "0.1.6"]]
                    :env {:squiggly {:checkers [:eastwood :kibit]
                                     :eastwood-options {:add-linters [:unused-locals
                                                                      :unused-fn-args
                                                                      :unused-private-vars
                                                                      :keyword-typos]}}}}}
  :plugins [[lein-environ "1.0.0"]])

