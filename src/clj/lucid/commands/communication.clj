(ns lucid.commands.communication
  (:require [lucid.commands.helpers :refer [defcommand]]))

;; TODO infer correct ending punctuation
(defcommand say [message]
  (let [{:keys [states descriptors]} $server-info
        speaker-name (or
                       (get-in states [$self :value :login :character-name])
                       $self)]
    (doseq [destination-descriptor-id (keys descriptors)]
      ($sendln! destination-descriptor-id
        (str
          (if (= destination-descriptor-id $self) "You" speaker-name)
          " said \""
          message
          "\"")))))

