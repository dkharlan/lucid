(ns lucid.server.telnet
  (:require [clojure.string :as string]
            [taoensso.timbre :as log]
            [automat.core :as a]
            [manifold.stream :as s]
            [lucid.util :as util]
            [lucid.server.methods :as m]
            [lucid.server.colors :as colors]
            [aleph.tcp :as tcp]))

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

(def ^:private any-byte (a/range -128 127))
(def ^:private iac (unchecked-byte 0xFF))
(def ^:private non-iac (a/difference any-byte iac))
(def ^:private assert-option (a/range (unchecked-byte 0xFB) (unchecked-byte 0xFE)))
(def ^:private sub-negotiation-start (unchecked-byte 0xFA))
(def ^:private sub-negotiation-end (unchecked-byte 0xF0))
(def ^:private iac-other (a/difference any-byte iac assert-option sub-negotiation-start))
(def ^:private carriage-return-byte (first (.getBytes "\r")))
(def ^:private newline-byte (first (.getBytes "\n")))
(def ^:private non-newline (a/difference any-byte carriage-return-byte newline-byte))

(def ^:private telnet-fsm
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

(def ^:private telnet (partial a/advance telnet-fsm))

(defn- take-char [value input]
  (log/trace :take-char value input)
  (update-in value [:chars] conj input))

(defn- take-str [{:keys [chars] :as value} _]
  (log/trace :take-str value _)
  (let [str (-> chars (byte-array) (String. "UTF-8"))]
    (-> value
      (update-in [:strs] conj str)
      (assoc :chars []))))

(def ^:private lines-fsm
  (a/compile
    (a/+
      (a/or
        [[[(a/+ non-newline) (a/$ :take-char)] (a/? carriage-return-byte) newline-byte] (a/$ :take-str)]
        [[(a/? carriage-return-byte) newline-byte] (a/$ :take-str)]))
    {:reducers {:take-char take-char :take-str take-str}}))

(def ^:private lines (partial a/advance lines-fsm))

(defn- escape-color-codes [message]
  (string/join
    (loop [input-chars  message
           output-chars []]
      (if (empty? input-chars)
        output-chars
        (let [[next & others]   input-chars
              escape?           (= next colors/color-code-start)
              next-input-chars  (if escape?
                                  (rest others)
                                  others)
              next-output-chars (if escape?
                                  (if-let [escape-sequence (->> others
                                                             (first)
                                                             (conj '(:tcp))
                                                             (get-in colors/color-codes))]
                                    (into output-chars escape-sequence)
                                    output-chars)
                                  (conj output-chars next))]
          (recur next-input-chars next-output-chars))))))

(defmethod m/make-output-stream :telnet [_ stream close-handler]
  (let [output-stream (s/stream)]
    (s/connect-via
      output-stream
      #(s/put! stream (-> % (escape-color-codes) (str "\n")))
      stream)
    (s/on-closed output-stream close-handler)
    output-stream))

;; TODO combine these last two
;; TODO how to prevent preferential treatment of socket streams? figure out which thread this is called on
;; TODO think about error handling
(defn- telnet-handler! [leftovers buffer transform message-bytes]
  (try
    (let [{:keys [strs chars]} (->> message-bytes
                                 (concat @leftovers)
                                 (reduce telnet [])
                                 (:value)
                                 (reduce lines {:strs [] :chars []})
                                 (:value))]
      (doseq [str strs]
        (->> str
          (transform)
          (s/put! buffer)))
      (reset! leftovers chars))
    (catch Exception ex
      (log/error ex)
      (throw ex))))

;; TODO may want to keep track of the leftovers atom someday
(defmethod m/make-message-handler :telnet [_ message-buffer message-xform]
  (partial telnet-handler!
    (atom [])
    message-buffer
    message-xform))

(defn make-server [acceptor port]
  (let [acceptor #(acceptor %1 (assoc %2 :type :telnet))]
    (delay
      (tcp/start-server acceptor {:port port}))))

