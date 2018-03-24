(ns lucid.commands.helpers)

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

