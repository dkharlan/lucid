(defproject lucid "0.1.0-SNAPSHOT"
  :description "A flexible MUD framework written in Clojure"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-time "0.14.2"]
                 [reduce-fsm "0.1.4"]
                 [com.datomic/datomic-free "0.9.5656"
                  :exclusions [org.hornetq/hornetq-server]] ;; see netty note below
                 [org.clojure/data.codec "0.1.0"]
                 ;;[crypto-random "1.2.0"]
                 [buddy/buddy-hashers "1.0.0"]
                 ;; see https://groups.google.com/forum/#!topic/datomic/pZombLbp-tQ
                 ;; for the next two lines
                 [io.netty/netty-all "4.1.2.Final"]
                 [org.clojars.markdingram/hornetq-server "2.4.8.Final"]
                 [aleph "0.4.4"]
                 [com.taoensso/timbre "4.10.0"]
                 [manifold "0.1.6"]
                 [danlentz/clj-uuid "0.1.6"]]
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

