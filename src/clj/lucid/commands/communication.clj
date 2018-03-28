(ns lucid.commands.communication
  (:require [lucid.commands.helpers :refer [defcommand]]
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
        (->> states
          (vals)
          (map #(get-in % [:value :login]))
          (filter #(get % :character-name))
          (map (fn [{:keys [character-name descriptor-id]}]
                 [character-name descriptor-id]))
          (into {}))

        player-names
        (map first
          (db/q '[:find ?online-player-name 
                  :in $ ?speaker-name [?online-player-name ...]
                  :where [?speaker :character/name ?speaker-name]
                         [?speaker :character/body ?speaker-body]
                         [?speaker-body :body/location ?speaker-location]
                         [?other-player-body :body/location ?speaker-location]
                         [?other-player :character/body ?other-player-body]
                         [?other-player :character/name ?other-player-name]
                         [(= ?online-player-name ?other-player-name)]]
            db
            speaker-name
            (keys players->descriptors)))]

    (doseq [player-name player-names]
      (let [descriptor-id (get players->descriptors player-name)
            speaker-name  (if (= descriptor-id $self) "You" speaker-name)]
        ($sendln! descriptor-id
          (str speaker-name " said \"" message "\""))))))

