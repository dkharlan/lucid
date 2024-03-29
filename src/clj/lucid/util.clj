(ns lucid.util
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn log-and-return!
  ([thing]
   (log/debug thing)
   thing)
  ([prefix thing]
   (log/debug prefix thing)
   thing))

;; TODO more info 
(defn thread-info$ [^Thread t]
  {:pre [(not (nil? t))]}
  {:name (.getName t)
   :obj  t})

(defn current-thread-info$ []
  (thread-info$ (Thread/currentThread)))

(defn all-threads$ []
  (.keySet (Thread/getAllStackTraces)))

(defn all-thread-info$ []
  (map thread-info$ (all-threads$)))

;; adapted from taoensso.timbre/default-output-fn as of [com.taoensso/timbre "4.10.0"]
;; TODO add middle ellipsis and padding for thread name to keep logs aligned
(defn logger-output-with-thread 
  ([     data] (logger-output-with-thread nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
                 timestamp_ ?line]} data
         thread-name (.getName ^Thread (Thread/currentThread))]
     (str
             (force timestamp_)       " "
             (force hostname_)        " "
       (format "%-5s" (log/color-str :yellow (str/upper-case (name level))))   " "
       "[" (or ?ns-str ?file "?") ":" (or ?line "?") "]"
       "(" thread-name ")"
       " - "
       (force msg_)
       (when-not no-stacktrace?
         (when-let [err ?err]
           (str "\n" (log/stacktrace err opts))))))))

