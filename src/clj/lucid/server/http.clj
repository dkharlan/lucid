(ns lucid.server.http
  (:import [io.netty.handler.codec.http.websocketx WebSocketHandshakeException])
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [aleph.http :as http]
            [reduce-fsm :refer [fsm-inc]]
            [compojure.core :refer [GET routes]]
            [compojure.route :as route]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [lucid.states.helpers :as st]
            [lucid.server.colors :as colors]
            [lucid.server.methods :as m]))

(defn- take-char [value input _ _]
  (update-in value [:chars] conj input))

(defn- take-string [{escape :escape cs :chars :as value} _ _ _]
  (-> value
    (assoc-in [:chars] [])
    (update-in [:strs] conj (cond-> (string/join cs)
                              escape (#(do {:color escape :text %}))))))

(defn- take-line [value input prev-state next-state]
  (let [{:keys [strs] :as value} (take-string value input prev-state next-state)]
    (-> value
      (assoc-in [:strs] [])
      (update-in [:lines] conj
        (if (= (count strs) 1)
          (first strs)
          strs)))))

(defn- set-escape-type [{cs :chars :as value} input prev-state next-state]
  (cond-> value
    (not-empty cs) (take-string input prev-state next-state)
    true           (assoc :escape
                     (get-in colors/color-codes [input :http]))))

(def ^:private http-colors
  (fsm-inc
    [[:input
      :end     -> {:action take-line}       :input
      \$       ->                           :escaping
      \newline -> {:action take-line}       :input
      _        -> {:action take-char}       :input]
     [:escaping
      _        -> {:action set-escape-type} :input]]))


(defn- websocket-handler [new-connection-handler! {:keys [remote-addr] :as request}]
  (d/catch
      (d/let-flow [socket (http/websocket-connection request)
                   info   {:remote-addr remote-addr}] ;; TODO anything else for info?
        (log/debug "Websocket connection request from" remote-addr)
        (new-connection-handler! socket info))
      #(try
         (throw %)
         (catch WebSocketHandshakeException _
           (log/warn "Non-websocket request where websockets was expected from" remote-addr)
           {:status 400
            :headers {"Content-Type" "application/text"}
            :body "Websocket request expected"})
         (catch Exception ex
           (log/error ex)))))

(defn make-routes [new-connection-handler!]
  (routes
    (GET "/connect" request
      (websocket-handler new-connection-handler! request))))

;; TODO the complected close handlers here make this prone to deadlock.
;; TODO using s/connect instead of s/consume would probably obviate the
;; TODO need to do this anyway
(defmethod m/make-output-stream :http [_ stream close-handler]
  (let [output-stream    (s/stream)
        http-color-state (atom
                           (http-colors {:chars [] :strs [] :lines []}))]
    (s/consume 
      (fn [message]
        (let [{{:keys [lines]} :value :as next-state}
              (st/reduce-fsm @http-color-state (-> message (vec) (conj :end)))]
          (reset! http-color-state
            (assoc-in next-state [:value :lines] []))
          (doseq [line lines]
            (s/put! stream (prn-str line)))))
      output-stream)
    (s/on-closed output-stream
      (fn []
        (close-handler)
        (log/trace "Trying to close HTTP output stream")
        (s/close! stream)  ;; TODO saw one instance of deadlock here
        (log/trace "Closed HTTP output stream")))
    (s/on-closed stream
      (fn []
        (log/trace "Trying to close HTTP websocket stream")
        (s/close! output-stream)
        (log/trace "Closed HTTP websocket stream")))
    output-stream))

;; TODO combine these last two
(defn- websocket-message-handler! [buffer transform message]
  (->> message
    (transform)
    (s/put! buffer)))

(defmethod m/make-message-handler :http [_ message-buffer message-xform]
  (partial websocket-message-handler!
    message-buffer
    message-xform))

(defn make-server [acceptor port]
  (let [acceptor #(acceptor %1 (assoc %2 :type :http))]
    (delay
      (-> acceptor
        (make-routes)
        (http/start-server {:port port})))))

