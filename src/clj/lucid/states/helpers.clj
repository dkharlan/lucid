(ns lucid.states.helpers
  (:require [reduce-fsm :as fsm]))

(defn inc-fsm [state input]
  (fsm/fsm-event state input))

(defn reduce-fsm [state coll]
  (reduce inc-fsm state coll))

(defn queue-stream-send [accumulator desc message]
  (update-in accumulator [:side-effects :stream] conj {:destination desc :message message}))

(defn queue-stream-send-to-self [accumulator message]
  (let [self (get-in accumulator [:login :descriptor-id])]
    (queue-stream-send accumulator self message)))

(defn queue-log-event [accumulator level & messages]
  (update-in accumulator [:side-effects :log] conj {:level level :messages messages}))

(defn queue-db-transaction [accumulator txn]
  (update-in accumulator [:side-effects :db] conj txn))

(defn queue-db-transactions [accumulator txns]
  (update-in accumulator [:side-effects :db] (comp vec concat) txns))

