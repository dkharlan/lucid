(ns lucid.states.helpers
  (:require [reduce-fsm :as fsm]
            [datomic.api :as db]
            [lucid.queries :as q]))

(defn inc-fsm [state input]
  (fsm/fsm-event state input))

(defn reduce-fsm [state coll]
  (reduce inc-fsm state coll))

(defn queue-stream-send [accumulator desc message]
  (update-in accumulator [:side-effects :stream] conj {:destination desc :message message}))

(defn queue-stream-multiple-sends [accumulator stream-side-effects]
  (update-in accumulator [:side-effects :stream] (comp vec concat) stream-side-effects))

(defn queue-stream-send-to-self [accumulator message]
  (let [self (get-in accumulator [:login :descriptor-id])]
    (queue-stream-send accumulator self message)))

(defn queue-log-event [accumulator level & messages]
  (update-in accumulator [:side-effects :log] conj {:level level :messages messages}))

(defn queue-db-transaction [accumulator txn]
  (update-in accumulator [:side-effects :db] conj txn))

(defn queue-db-transactions [accumulator txns]
  (update-in accumulator [:side-effects :db] (comp vec concat) txns))

(defn players->descriptors [states]
  (->> states
    (vals)
    (map #(get-in % [:value :login]))
    (filter #(get % :character-name))
    (map (fn [{:keys [character-name descriptor-id]}]
           [character-name descriptor-id]))
    (into {})))

;; TODO not sure if this goes here...
(defn broadcast-near [{:keys [db states]} target-player-name message]
  (let [players->descriptors (players->descriptors states)
        nearby-players       (map first
                               (db/q q/nearby-players-without-target
                                 db
                                 target-player-name
                                 (keys players->descriptors)))]
    (vec (for [other-player-name nearby-players]
           {:destination (get players->descriptors other-player-name)
            :message     message}))))

