(ns lucid.server.colors
  (:require [clojure.string :as string]))

;; TODO needs to support http as well

(def color-code-start \$)

(def color-codes
  {;; dark red          light red
   \r "\033[31m"        \R "\033[31;1m"

   ;; dark green        light green
   \g "\033[32m"        \G "\033[32;1m"

   ;; dark yellow       light yellow
   \y "\033[33m"        \Y "\033[33;1m"

   ;; dark blue         light blue
   \b "\033[34m"        \B "\033[34;1m"

   ;; purple            pink
   \p "\033[35m"        \P "\033[35;1m"

   ;; dark cyan         light cyan
   \c "\033[36m"        \C "\033[36;1m"

   ;; gray              white
   \w "\033[37m"        \W "\033[37;1m"

   ;; reset
   \! "\033[0m"

   ;; identity
   color-code-start
   (str color-code-start)})

(defn escape-color-codes [message]
  (string/join
    (loop [input-chars  message
           output-chars []]
      (if (empty? input-chars)
        output-chars
        (let [[next & others]   input-chars
              escape?           (= next color-code-start)
              next-input-chars  (if escape?
                                  (rest others)
                                  others)
              next-output-chars (if escape?
                                  (if-let [escape-sequence (->> others (first) (get color-codes))]
                                    (into output-chars escape-sequence)
                                    output-chars)
                                  (conj output-chars next))]
          (recur next-input-chars next-output-chars))))))

