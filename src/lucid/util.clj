(ns lucid.util)

(def bytes->string [bytes]
  (-> bytes
    (String. "UTF-8")
    (clojure.string/trim)))

