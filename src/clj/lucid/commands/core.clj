(ns lucid.commands.core
  (:require [clojure.string :as string]
            [lucid.database :refer [speculate]]
            [lucid.commands.parser :as p]
            [lucid.commands.helpers :refer [defcommand]]
            [lucid.commands.perception :as perc]
            [lucid.commands.information :as info]
            [lucid.commands.communication :as comm]))

(declare commands)
(declare help)

(def command-table
  {"say"      #'comm/say
   "look"     #'perc/look
   "who"      #'info/who
   "commands" #'commands
   "help"     #'help})

(defcommand commands []
  {:help {:short "Lists available commands"
          :long  "$CCOMMANDS$! Lists the commands your player can use (not necessarily every command)."}}
  ($sendln! $self "$!The following commands are available:\n")
  (let [commands
        (->> command-table
          (vals)
          (map var-get)
          (map meta)
          (sort-by :command-name))

        command-name-max-length
        (->> commands
          (map :command-name)
          (map count)
          (apply max))]

    (doseq [{:keys [command-name help]} commands]
      ($sendln! $self
        (str
          (format (str "  $c%-" command-name-max-length "s$!") command-name)
          " - "
          (:short help))))))

;; TODO expand to more sources than just the command table
(defcommand help [command]
  {:help {:short "Explain a command"
          :long  "$CHELP$! describes a command and its arguments."}}
  (let [{:keys [help argspec] :as command-data}
        (-> command-table
          (get command)
          (var-get)
          (meta))]

    (if-not command-data
      ($sendln! $self (str "No such command as '" command "'."))
      (let [args (->> argspec
                   (map name)
                   (interpose " ")
                   (apply str))]
        ($sendln! $self (:long help))
        ($sendln! $self "\n$WSyntax:$!")
        ($sendln! $self (str "  $C" command "$w " args "$!"))))))

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

