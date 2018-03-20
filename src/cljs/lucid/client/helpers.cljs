(ns lucid.client.helpers)

(defn scrolled-to-bottom? [element]
  (>= (.-scrollTop element)
    (- (.-scrollHeight element) (.-offsetHeight element))))

(defn scroll-to-bottom! [element]
  (set! (.-scrollTop element) (.-scrollHeight element)))

