(ns lucid.characters
  (:require [buddy.hashers :as hash]
            [lucid.queries :as q]
            [taoensso.timbre :as log]))

(defn character-exists? [character-name]
  (and
    (string? character-name)
    (q/character-exists? character-name)))

(defn password-is-valid? [character-name offered-password]
  (let [{:keys [character/password-hash-and-salt]} (q/get-character character-name)]
    (hash/check offered-password password-hash-and-salt)))

(defn create-character [character-name password]
  (log/debug character-name)
  (log/debug password)
  (q/create-character
    character-name
    (hash/derive password)))

