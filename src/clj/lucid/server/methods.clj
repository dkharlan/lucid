(ns lucid.server.methods)

(defmulti make-output-stream
  (fn [connection-type _ _]
    connection-type))

(defmethod make-output-stream :default [connection-type _ _]
  (throw
    (UnsupportedOperationException.
      (str "Unkown connection type " connection-type))))

(defmulti make-message-handler
  (fn [connection-type _ _]
    connection-type))

(defmethod make-message-handler :default [connection-type _ _]
  (throw
    (UnsupportedOperationException.
      (str "Unkown connection type " connection-type))))

