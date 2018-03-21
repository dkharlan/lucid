(ns lucid.commands.core
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]))

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
  "
  [name args & body]
  (let [accumulator-sym (gensym "accumulator")]
    `(def ~name
       (with-meta
         (fn ~(into [accumulator-sym] args) 
           (let [side-effects#
                 (atom {})

                 ~'$self
                 (get-in ~accumulator-sym [:login :descriptor-id])

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

(defcommand say [message]
  ($sendln! $self (str "You said: " message)))

(def command-table
  {"say" say})

(defn parse [accumulator {:keys [message]} _ _]
  (let [self-desc-id     (get-in accumulator [:login :descriptor-id])
        [command & args] (string/split message #"\s+")
        command-fn       (get command-table command)

        offered-args     (count args)
        command-args     (-> command-fn (meta) (:arity))

        send-to-self     #(update-in accumulator [:side-effects :stream] conj
                            {:destination self-desc-id :message %})]
    (log/debug "Command, args, command-fn:" command args command-fn)
    (cond
      (not command-fn)
      (send-to-self "No such command.")
      
      (not= offered-args command-args)
      (send-to-self
        (str "You sent " offered-args " arguments, while \"" command "\" requires " command-args "."))

      :ok
      (apply command-fn accumulator args))))

