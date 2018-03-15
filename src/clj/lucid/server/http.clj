(ns lucid.server.http
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
      (fn [_]
        (log/warn "Non-websocket request where websockets was expected from" remote-addr)
        {:status 400
         :headers {"Content-Type" "application/text"}
         :body "Websocket request expected"})))

(defn make-routes [new-connection-handler!]
  (routes
    (GET "/connect" request
      (websocket-handler new-connection-handler! request))))

