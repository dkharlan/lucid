(ns lucid.server.http
  (:require [taoensso.timbre :as log]
            [compojure.core :refer [GET routes]]
            [compojure.route :as route]
            [manifold.deferred :as d]
            [manifold.stream :as s]))

(defn websocket-handler [{:keys [remote-addr] :as request}]
  (log/info "Websocket connection request from" remote-addr)
  (d/catch
      (d/let-flow [socket (http/websocket-connection request)]
        (s/connect socket socket)
        (s/on-closed socket #(log/info "Closed connection from" remote-addr)))
      (fn [_]
        {:status 400
         :headers {"Content-Type" "application/text"}
         :body "Websocket request expected"})))

(def app
  (routes
    (GET "/connect" request
      (websocket-handler request))))

