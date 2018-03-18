(ns lucid.server.http
  (:import [io.netty.handler.codec.http.websocketx WebSocketHandshakeException])
  (:require [taoensso.timbre :as log]
            [aleph.http :as http]
            [compojure.core :refer [GET routes]]
            [compojure.route :as route]
            [manifold.deferred :as d]
            [manifold.stream :as s]))

(defn websocket-handler [new-connection-handler! {:keys [remote-addr] :as request}]
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

(defn websocket-message-handler! [buffer transform message]
  (->> message
    (transform)
    (s/put! buffer)))

