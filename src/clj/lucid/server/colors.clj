(ns lucid.server.colors
  (:require [clojure.string :as string]))

;; TODO needs to support http as well

(def color-code-start \$)

(def color-codes
  {;; dark red
   \r "\033[31m"

   ;; reset
   \! "\033[0m"})

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

