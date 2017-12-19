(ns lucid.characters
  (:require [buddy.hashers :as hash]
            [lucid.queries :as q]))

(defn character-exists? [[_ character-name]]
  (and
    (string? character-name)
    (q/character-exists? character-name)))

(defn password-is-valid? [[{{:keys [character-name]} :login} offered-password]]
  (let [{:keys [character/password-hash-and-salt]} (q/get-character character-name)]
    (hash/check offered-password password-hash-and-salt)))

(defn create-character! [{:keys [character-name initial-password]}]
  (let [password-hash-and-salt (hash/derive initial-password)]
    (q/create-character! character-name password-hash-and-salt)))
