(defproject lucid "0.1.0-SNAPSHOT"
  :description "A flexible MUD framework written in Clojure"
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 [clj-time "0.12.0"]
                 [reduce-fsm "0.1.4"]
                 [com.datomic/datomic-free "0.9.5394"]]
  :main ^:skip-aot lucid.core
  :target-path "target/%s"
  :repl-options {:init (do
                         (require '[alembic.still :as alembic]))}
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[alembic "0.3.2"]]}})

