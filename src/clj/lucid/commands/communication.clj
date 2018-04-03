(ns lucid.commands.communication
  (:require [lucid.commands.helpers :refer [defcommand]]
            [lucid.states.helpers :as h]
            [lucid.queries :as q]
            [datomic.api :as db]))

;; TODO infer correct ending punctuation?
(defcommand say [message]
  {:help {:short "Speak to nearby characters"
          :long  "$CSAY$! allows you to speak to characters in the same room as you."}}
  (let [{:keys [states db]}
        $server-info

        speaker-name
        (get-in states [$self :value :login :character-name])

        players->descriptors
        (h/players->descriptors states)

        player-names
        (map first
          (db/q q/nearby-players-with-target
            db
            speaker-name
            (keys players->descriptors)))]

    (doseq [player-name player-names]
      (let [descriptor-id (get players->descriptors player-name)
            speaker-name  (if (= descriptor-id $self) "You" speaker-name)]
        ($sendln! descriptor-id
          (str speaker-name " said \"" message "\""))))))

