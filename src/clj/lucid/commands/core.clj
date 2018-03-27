(ns lucid.commands.core
  (:require [clojure.string :as string]
            [lucid.commands.parser :as p]
            [lucid.commands.communication :as comm]
            [lucid.commands.perception :as per]))

(def command-table
  {"say"  #'comm/say
   "look" #'per/look})

(defn command-action [acc {:keys [message server-info]} _ _]
  (let [self-desc-id     (get-in acc [:login :descriptor-id])
        send-to-self     #(update-in %1 [:side-effects :stream] conj
                            {:destination self-desc-id :message %2})

        [command args]   (string/split message #"\s+" 2)
        command-fn       (var-get (get command-table command))]
    (if-not command-fn
      (send-to-self acc "No such command.")
      (let [command-arity    (-> command-fn (meta) (:arity))
            {remaining-args :remaining-args
             parsed-args     :strings}
            (p/parse command-arity args)]
        ;; TODO pull true case handler from command metadata
        (if (pos? remaining-args)
          (send-to-self acc
            (str "'" command "' takes "
              command-arity (if (> command-arity 1) " arguments" " argument")
              " but you only provided " (count parsed-args) "."))
          (->> parsed-args
            (take command-arity)
            (apply command-fn acc server-info)))))))

