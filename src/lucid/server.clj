(ns lucid.server
  (:require [taoensso.timbre :as log]
            [automat.core :as a]
            [clj-uuid :as uuid]
            [aleph.tcp :as tcp]
            [manifold.stream :as s]
            [lucid.util :as util]))

;; TODO move the telnet state machine to another namespace

;; the first of two state machines needed will be to keep track of the telnet protocol state.
;; for now, this is going to just ignore everything
;;    IAC IAC -> data byte 255 (2 bytes)
;;    IAC WILL/WONT/DO/DONT xxx -> ignore (3 bytes)
;;    IAC SB xxx ... IAC SE -> ignore (variable) 
;;    IAC other -> ignore -> other
;;
;;    IAC   = 255 = 0xFF
;;    WILL  = 251 = 0xFB
;;    WONT  = 252 = 0xFC
;;    DO    = 253 = 0xFD
;;    DON'T = 254 = 0xFE
;;
;; will probably eventually want to support option negotiation and subnegotiationa
;; TODO implement UTF-8

;; TODO make these private
(def any-byte (a/range -128 127))
(def iac (unchecked-byte 0xFF))
(def non-iac (a/difference any-byte iac))
(def assert-option (a/range (unchecked-byte 0xFB) (unchecked-byte 0xFE)))
(def sub-negotiation-start (unchecked-byte 0xFA))
(def sub-negotiation-end (unchecked-byte 0xF0))
(def iac-other (a/difference any-byte iac assert-option sub-negotiation-start))
(def carriage-return-byte (first (.getBytes "\r")))
(def newline-byte (first (.getBytes "\n")))
(def non-newline (a/difference any-byte carriage-return newline-byte))

;; TODO make this private
(def telnet
  (a/compile
    (a/+
      (a/or
        [non-iac (a/$ :take)]
        [iac
         (a/or
           [iac (a/$ :take)]
           [assert-option any-byte]
           [sub-negotiation-start (a/* non-iac) iac sub-negotiation-end]
           iac-other)]))
    {:reducers {:take #(conj %1 %2)}})) ;; TODO stare suspiciously at :take with a profiler

;; TODO make this private
;; TODO make this take as many full lines as exist
(def line
  (a/compile
    [[(a/+ non-newline) (a/$ :take)] (a/? carriage-return-byte) newline-byte (a/* any-byte)]
    {:reducers {:take #(conj %1 %2)}}))

;; TODO how to prevent preferential treatment of socket streams?
;; TODO combine telnet and line efficiently to grab lines from the input buffer, put them into the
;; TODO the fsm atom could be created in accept-new-connection! and partially applied to this -- this will prevent swap madness for descriptors atom
(defn telnet-handler [message-bytes]
  )

;; TODO will also need to keep track of connection state(s)
(defn- make-descriptor [id stream]
  {:id id :stream stream})

(defn- close! [descriptor descriptor-id info]
  (swap! descriptor dissoc descriptor-id)
  (log/debug "Connection from" (:remote-addr info) "closed"))

(defn- make-message [descriptor-id message]
  {:descriptor-id descriptor-id :message (util/bytes->string message)})

(defn insert-into! [destination transform bytes]
  (log/debug (type bytes))
  (->> bytes
    (transform) ;; TODO on which thread is this called? the destination's or source's?
    (s/put! destination)))

(defn- accept-new-connection! [descriptors message-buffer stream info]
  (log/debug "New connection initiated:" info)
  (let [descriptor-id       (uuid/v1)
        make-message*       (partial make-message descriptor-id)
        insert-into-buffer! (partial insert-into! message-buffer make-message*)]
    (s/on-closed stream (partial close! descriptors descriptor-id info))
    (s/connect-via stream insert-into-buffer! message-buffer)
    (swap! descriptors assoc descriptor-id (make-descriptor descriptor-id stream))
    (log/info "Accepted new connection from descriptor" descriptor-id "at" (:remote-addr info))))

;; TODO see if there's an easier / more succint / more idiomatic way to do this
(defn- collect! [stream]
  (loop [messages []]
    (let [message @(s/try-take! stream nil 0 ::nothing)] 
      (if (or (nil? message) (= message ::nothing)) 
        messages
        (recur (conj messages message))))))

(defn- update! [descriptors message-buffer updater-signal]
  (log/info "Update thread started.")
  (while (let [signal @(s/try-take! updater-signal nil 0 ::nothing)]
           (and (not= ::shutdown signal) (not (nil? signal))))
    (let [messages (collect! message-buffer)]
      (doseq [{:keys [descriptor-id message] :as msg} messages]
        ;; TODO input processing logic goes here
        (log/debug "Message:" msg)
        (log/info descriptor-id "says" (str "\"" message "\""))
        (doseq [{:keys [id stream]} (vals @descriptors)]
          (when (not= id descriptor-id)
            (log/debug "Sending to" id)
            (s/put! stream (str descriptor-id " says " (str "\"" message "\"") "\n"))))))
    (Thread/sleep 100)) ;; TODO replace Thread/sleep with something more robust
  ;; TODO any cleanup goes here
  (log/info "Update thread finished cleaning up."))

(defn- server* [descriptors message-buffer acceptor tcp-server update-thread updater-signal]
  {:descriptors descriptors
   :message-buffer message-buffer
   :acceptor acceptor
   :tcp-server tcp-server
   :update-thread update-thread
   :updater-signal updater-signal})

(defn make-server [port]
  (let [descriptors    (atom {})
        message-buffer (s/stream* {:permanent? true :buffer-size 1000}) ;; TODO may have to fiddle with the buffer length
        acceptor       (partial accept-new-connection! descriptors message-buffer)
        tcp-server     (delay (tcp/start-server acceptor {:port port}))
        updater-signal (s/stream) ;; TODO what properties does this stream need? could see a buffer being necessary
        updater        (partial update! descriptors message-buffer updater-signal)
        update-thread  (Thread. updater "update-thread")]
    (server* descriptors message-buffer acceptor tcp-server update-thread updater-signal)))

(defn start! [{:keys [tcp-server update-thread] :as this}]
  (log/info "Launching update thread...")
  (.start update-thread)
  @tcp-server
  (log/info "TCP socket server has been started.")
  this)

(defn stop! [{:keys [descriptors tcp-server updater-signal]}]
  (doseq [{:keys [stream]} (vals @descriptors)]
    (s/close! stream))
  (.close @tcp-server)
  (log/info "TCP socket server shutdown.")
  (reset! descriptors nil)
  (case @(s/try-put! updater-signal ::shutdown 5000 ::timeout)
    true      (log/info  "Update thread has received shutdown signal.")
    ::timeout (log/error "Update thread did not acknowledge shutdown signal!")
    false     (log/error "Failed to notify update thread of shutdown!")))

