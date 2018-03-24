(ns lucid.commands.core
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [lucid.commands.parser :as p]))

;; TODO add an options map as an optional third parameter (after the args vector)
(defmacro defcommand
  "Defines a command, which is a variable-arity reducer over the state accumulator (see lucid.states).
   The body uses anaphora to accumulate side effect values without having to return them explicitly,
   which makes command definitions easier to read and write.  The resulting functions can be pure,
   which allows speculative execution.
  
   The following anaphora are available:

   ($sendln! destination message)
   Adds a stream output side effect to destination containing message

   ($log! level message-one message-two ...)
   Adds a log side effect with the desired level keyword

   ($persist! [txn1 txn2 ...])
   Queues the specified DB transactions

   $self
   The descriptor ID of the player executing the command

   $server-info
   A map containing information about the current server states.  See lucid.server.core for details.
  "
  [name args & body]
  (let [accumulator-sym (gensym "accumulator")
        server-info-sym (gensym "server-info")]
    `(def ~name
       (with-meta
         (fn ~(into [accumulator-sym server-info-sym] args) 
           (let [side-effects#
                 (atom {})

                 ~'$self
                 (get-in ~accumulator-sym [:login :descriptor-id])

                 ~'$server-info
                 ~server-info-sym

                 ~'$sendln!
                 (fn [destination# message#]
                   (swap! side-effects# update-in [:stream] conj
                     {:destination destination# :message message#}))

                 ~'$log!
                 (fn [level# & messages#]
                   (swap! side-effects# update-in [:log] conj
                     {:level level# :messages messages#}))

                 ~'$persist!
                 (fn [transactions#]
                   (swap! side-effects# update-in [:db] concat transactions#))]

             ~@body
             (update-in ~accumulator-sym [:side-effects]
               #(merge-with concat %1 %2)
               @side-effects#)))
         {:arity ~(count args)}))))

;; TODO infer correct ending punctuation
(defcommand say [message]
  (let [{:keys [states descriptors]} $server-info
        speaker-name (or
                       (get-in states [$self :value :login :character-name])
                       $self)]
    (doseq [destination-descriptor-id (keys descriptors)]
      ($log! :info "Will be sending to" destination-descriptor-id)
      ($sendln! destination-descriptor-id
        (str
          (if (= destination-descriptor-id $self) "You" speaker-name)
          " said \""
          message
          "\"")))))

(def command-table
  {"say" say})

(defn parse [acc {:keys [message server-info]} _ _]
  (let [self-desc-id     (get-in acc [:login :descriptor-id])
        send-to-self     #(update-in %1 [:side-effects :stream] conj
                            {:destination self-desc-id :message %2})

        [command args]   (string/split message #"\s+" 2)
        command-fn       (get command-table command)]
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
          (apply command-fn acc server-info parsed-args))))))

