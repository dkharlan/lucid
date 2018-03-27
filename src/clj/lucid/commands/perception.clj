(ns lucid.commands.perception
  (:require [clojure.string :as string]
            [datomic.api :as db]
            [lucid.commands.helpers :refer [defcommand]]))

;; TODO see if these queries can be combined
(defcommand look []
  (let [{:keys [states db]}
        $server-info

        self-name
        (get-in states [$self :value :login :character-name])

        room
        (db/q '[:find (pull ?r [:room/name :room/description]) .
                :in $ ?cn
                :where [?c :character/name ?cn]
                       [?c :character/body ?b]
                       [?b :body/location ?r]]
          db
          self-name)

        inhabitants
        (map first
          (db/q '[:find ?logged-in-cn
                  :in $ ?cn [?logged-in-cn ...]
                  :where [?c :character/name ?cn]
                         [?c :character/body ?cb]
                         [?cb :body/location ?r]
                         [?b :body/location ?r]
                         [?logged-in-c :character/body ?b]
                         [?logged-in-c :character/name ?logged-in-cn]
                         [(!= ?b ?cb)]]
            db
            self-name
            (map #(get-in % [:value :login :character-name]) (vals states))))

        number-of-inhabitants
        (count inhabitants)]

    ($sendln! $self (str "$B" (:room/name room) "$!"))
    ($sendln! $self (:room/description room))
    (when (pos? number-of-inhabitants)
      ($sendln! $self
        (if (> number-of-inhabitants 1)
          (str (string/join ", " (butlast inhabitants)) " and " (last inhabitants) " are here.")
          (str (first inhabitants) " is here."))))))

