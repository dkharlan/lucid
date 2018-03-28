(ns lucid.commands.information
  (:require [clojure.string :as string]
            [lucid.commands.helpers :refer [defcommand]]))

(defcommand who []
  {:help {:short "Lists online players"
          :long  "$CWHO$! shows you the names of all characters that are currently playing."}}
  (let [players
        (->> $server-info
          (:states)
          (vals)
          (map #(get-in % [:value :login :character-name])))

        longest-name-length
        (apply max (map count players))

        vertical-separator
        (str "$w+$c" (string/join (repeat (+ longest-name-length 2) "-")) "$w+$!")]

    ($sendln! $self vertical-separator)
    (doseq [player players]
      ($sendln! $self
        (str
          "$c|$w "
          (format (str "%-" longest-name-length "s") player)
          " $c|$!")))
    ($sendln! $self vertical-separator)
    ($sendln! $self (str "$!"(count players) " players are online."))))

