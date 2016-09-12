(ns lucid.characters
  (:require [buddy.hashers :as hash]
            [lucid.queries :as q]))

(defn character-exists? [[_ character-name]]
  (and
    (string? character-name)
    (q/does-character-exist? character-name)))

(defn password-is-valid? [[{{:keys [character-name]} :login} offered-password]]
  (let [actual-password-hash-and-salt (q/get-password-hash-and-salt-for-character character-name)]
    (hash/check offered-password actual-password-hash-and-salt)))

(defn create-character! [{:keys [character-name initial-password]}]
  (let [password-hash-and-salt (hash/derive initial-password)]
    (q/create-character! character-name password-hash-and-salt)))

