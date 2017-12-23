(ns lucid.server.telnet
  (:require [automat.core :as a]
            [manifold.stream :as s]
            [taoensso.timbre :as log]
            [lucid.util :as util]))

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

;; TODO make this private
(def ^:private telnet
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

(defn- take-char [value input]
  (update-in value [:chars] conj input))

(defn- take-str [{:keys [chars] :as value} _]
  (let [str (-> chars (byte-array) (String. "UTF-8"))]
    (-> value
      (update-in [:strs] conj str)
      (assoc :chars []))))

;; TODO make this private
(def ^:private lines
  (a/compile
    [[(a/+
        [[(a/+ non-newline) (a/$ :take-char)] (a/? carriage-return-byte) newline-byte])
      (a/$ :take-str)]
     (a/* any-byte)]
    {:reducers {:take-char take-char :take-str take-str}}))

;; TODO how to prevent preferential treatment of socket streams? figure out which thread this is called on
;; TODO wrap in try/catch block and log / handle errors
;; TODO advances need to be reduces
(defn telnet-handler! [leftovers buffer transform message-bytes]
  (try
    (let [{:keys [strs chars]} (->> message-bytes
                                 (concat @leftovers)
                                 (vec)
                                 (util/log-and-return!)
                                 (a/advance telnet [])
                                 (:value)
                                 (a/advance lines {:strs [] :chars []})
                                 (:value))]
      (doseq [str strs]
        (->> str
          (transform)
          (s/put! buffer)))
      (reset! leftovers chars))
    (catch Exception ex
      (log/error ex)
      (throw ex))))

