(ns lucid.commands.core
  (:require [clojure.string :as string]
            [lucid.database :refer [speculate]]
            [lucid.commands.parser :as p]
            [lucid.commands.helpers :refer [defcommand]]
            [lucid.commands.perception :as perc]
            [lucid.commands.information :as info]
            [lucid.commands.communication :as comm]))

(declare commands)

(def command-table
  {"say"      #'comm/say
   "look"     #'perc/look
   "who"      #'info/who
   "commands" #'commands})

(defcommand commands []
  {:help {:short "Lists available commands"
          :long  "$CCOMMANDS$! Lists the commands your player can use (not necessarily every command)."}}
  ($sendln! $self "$!The following commands are available:\n")
  (doseq [command-name (keys command-table)]
    ($sendln! $self (str "  $c" command-name "$!"))))

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

(defn call-command [accumulator command-name server-info & command-args]
  (let [command-fn   (var-get (get command-table command-name))
        pending-txns (get-in accumulator [:side-effects :db])]
    (apply command-fn
      accumulator
      (update server-info :db speculate pending-txns)
      command-args)))

