; Copyright 2013 Christian Kebekus
;
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.
(ns pubnub.impl
  "Please move on nothing to see here.

   These are just implementation details."
  (:refer-clojure :exclude [time])
  (:require [clojure.core.async :refer [>! <! >!! <!! chan close! go go-loop pub sub sliding-buffer]]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clj-http.lite.client :as http]
            [digest :as digest]
            [pubnub.crypto :as crypto])
  (:import [clojure.lang ExceptionInfo LazySeq PersistentArrayMap]
           [java.io IOException]
           [java.net URLEncoder]
           [java.util UUID]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identites

(def ^{:const true :private true} headers
  {"V"          "3.0"
   "User-Agent" "Clojure"
   "Accept"     "*/*"})

(def ^{:const true :private true} presence-channel-suffix "-pnpres")

(def ^{:private true} subscriptions (atom #{}))
(def ^{:private true} presences (atom #{}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Errors

(defmulti error-message type)

(defmethod error-message IOException [e]
  {:status :error :message (.getMessage e)})

(defmethod error-message PersistentArrayMap [e]
  {:status :error :message (:message e)})

(defmethod error-message LazySeq [e]
  {:status :error :message (second e)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn- create-signature
  [{:keys [publish-key subscribe-key secret-key channel]}]
  (digest/md5 (format "%s/%s/%s/%s"
                      publish-key
                      subscribe-key
                      secret-key
                      channel)))

;; TODO try to unify the request creation fns into one fn
;; maybe multi-method based on first parameter (action)?!?
;; maybe protocol dsl (macro) that creates coresponding fns?!?
(defn- publish-request
  [{:keys [publish-key subscribe-key channel origin ssl?] :as pn-channel} message]
  (let [protocol  (if ssl? "https" "http")
        signature (create-signature channel)
        callback  "0"]
    (format "%s://%s/publish/%s/%s/%s/%s/%s/%s"
            protocol
            origin
            publish-key
            subscribe-key
            signature
            channel
            callback
            message)))

(defn- subscribe-request
  [{:keys [subscribe-key channel client-id origin ssl?]} timetoken]
  (let [protocol (if ssl? "https" "http")
        callback "0"]
    (format "%s://%s/subscribe/%s/%s/%s/%s?uuid=%s"
            protocol
            origin
            subscribe-key
            channel
            callback
            timetoken
            client-id)))

(defn- presence-request
  [{:keys [subscribe-key channel client-id origin ssl?]} timetoken]
  (let [protocol (if ssl? "https" "http")
        callback "0"]
    (format "%s://%s/subscribe/%s/%s%s/%s/%s?uuid=%s"
            protocol
            origin
            subscribe-key
            channel
            presence-channel-suffix
            callback
            timetoken
            client-id)))

(defn- here-now-request
  [{:keys [subscribe-key channel origin ssl?]}]
  (let [protocol (if ssl? "https" "http")]
    (format "%s://%s/v2/presence/sub-key/%s/channel/%s"
            protocol
            origin
            subscribe-key
            channel)))

(defn- leave-request
  [{:keys [subscribe-key channel origin client-id ssl?]}]
  (let [protocol (if ssl? "https" "http")]
    (format "%s://%s/v2/presence/sub-key/%s/channel/%s/leave?uuid=%s"
            protocol
            origin
            subscribe-key
            channel
            client-id)))

(defn- time-request
  [{:keys [origin ssl?]}]
  (let [protocol (if ssl? "https" "http")]
    (format "%s://%s/time/0"
            protocol
            origin)))

(defn- pubnub-get
  [req]
  (http/get req {:headers headers}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn- leave
  "Send leave request for channel."
  [pn-channel]
  (let [res            (pubnub-get (leave-request pn-channel))
        {:keys [body]} res]
    (json/parse-string body true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

;; Publish/Subscribe

(defn subscribed?
  "Returns true if (PubNub) channel is currently subscribed."
  [pn-channel]
  (contains? @subscriptions pn-channel))

(defn publish
  [{:keys [encrypt-cipher] :as pn-channel} message]
  (try
    (let [enc-msg (if encrypt-cipher (crypto/encrypt pn-channel (json/generate-string message)) message)
          msg     (json/generate-string enc-msg)]
      (pubnub-get (publish-request pn-channel msg))
      {:status :ok :message "Sent"})
    (catch IOException ioe
      (error-message ioe))
    (catch ExceptionInfo ei
      (let [body (get-in (.getData ei) [:object :body])
            m    (json/parse-string body true)]
        (error-message m)))
    (catch Exception e
      e)))

(defn- subscribe*
  "Subscribe to PubNub channel."
  [{:keys [encrypt-cipher] :as pn-channel}]
  (let [c (chan)]
    (swap! subscriptions conj pn-channel)
    (go-loop [timetoken 0]
             (try
               (let [{:keys [body]}       (pubnub-get (subscribe-request pn-channel timetoken))
                     [msgs new-timetoken] (json/parse-string body true)]
                 (if (subscribed? pn-channel)
                   (do
                     (when (seq msgs)
                       (>! c {:status :ok :payload (mapv #(json/parse-string
                                                           (if encrypt-cipher
                                                             (crypto/decrypt pn-channel %)
                                                             %) true) msgs)}))
                     (recur new-timetoken))
                   (do
                     (leave pn-channel)
                     (close! c))))
               (catch IOException ioe
                 (>! c (error-message ioe)))
               (catch ExceptionInfo ei
                 (let [body (get-in (.getData ei) [:object :body])
                       m    (json/parse-string body true)]
                   (>! c (error-message m))))
               (catch Exception e
                 (>! c {:status :error :message "ERROR!"}))
               (finally
                 (swap! subscriptions disj pn-channel)
                 (close! c))))
    c))

(defn subscribe
  "Wires up the PubNub subscription to the passed in handler functions.

   This creates a (clojure.core.async) publication with two topics

   - :ok
   - :error

   The callback-fn is hooked up to the :ok topic
   and the error-fn to the :error topic (via two channels).

   In order for the channels not to block, the publication
   uses a sliding-buffer of 10 elements.

   The publication is based on the channel supplied by subscribe*."
  [pn-channel callback-fn error-fn]
  (when-not (subscribed? pn-channel)
    (let [msgs-ch     (subscribe* pn-channel)
          topic-fn    :status
          buffer-fn   (fn [topic] (sliding-buffer 10))
          publication (pub msgs-ch topic-fn buffer-fn)
          callback-ch (chan)
          error-ch    (chan)
          _           (sub publication :ok callback-ch false)
          _           (sub publication :error error-ch false)]
      (go (while (subscribed? pn-channel) (callback-fn (:payload (<! callback-ch)))))
      (go (while (subscribed? pn-channel) (error-fn (<! error-ch))))
      nil)))

(defn unsubscribe
  "Unsubscribe from the channel."
  [pn-channel]
  (swap! subscriptions disj pn-channel)
  nil)

;; Presence

(defn presence?
  "Returns true if (PubNub) channel is currently subscribed."
  [pn-channel]
  (contains? @presences pn-channel))

(defn- presence*
  "Subscribe to PubNub channel."
  [pn-channel]
  (let [c (chan)]
    (swap! presences conj pn-channel)
    (go-loop [timetoken 0]
             (let [{:keys [body]}         (pubnub-get (presence-request pn-channel timetoken))
                   ;; TODO: Events results are
                   ;; [{action join, timestamp 1381776169, uuid f8edab21-18c7-49c4-8436-62ea1af67673, occupancy 1}]
                   ;; [{action leave, timestamp 1381776169, uuid f8edab21-18c7-49c4-8436-62ea1af67673, occupancy 0}]
                   ;; [{action timeout, timestamp 1381776535, uuid f8edab21-18c7-49c4-8436-62ea1af67673, occupancy 1}]
                   ;; Should be converted to {:action "join" :client-id "f8edab21-18c7-49c4-8436-62ea1af67673"}
                   [events new-timetoken] (json/parse-string body true)]
               (if (presence? pn-channel)
                 (do
                   (when (seq events)
                     (>! c events))
                   (recur new-timetoken))
                 (close! c))))
    c))

(defn presence
  "Wires up PubNub presence events to the passed in handler functions.

   This creates a (clojure.core.async) publication with two topics

   - :ok
   - :error

   and hooks up the callback-fn to the ::success topic
   and the error-fn to the ::error topic (via two channels).

   In order for the channels not to block, the publication
   uses a sliding-buffer of 10 elements.

   The publication is based on the channel supplied by presence*."
  [pn-channel callback-fn error-fn]
  (when-not (presence? pn-channel)
    (let [msgs-ch     (presence* pn-channel)
          topic-fn    (fn [msg] ::success)
          buffer-fn   (fn [topic] (sliding-buffer 10))
          publication (pub msgs-ch topic-fn buffer-fn)
          callback-ch (chan)
          error-ch    (chan)
          _           (sub publication :ok callback-ch false)
          _           (sub publication :error error-ch false)]
      (go (while (presence? pn-channel) (callback-fn (<! callback-ch))))
      (go (while (presence? pn-channel) (error-fn (<! error-ch))))
      nil)))

(defn presence-unsubscribe
  "Unsubscribe from the presence channel."
  [pn-channel]
  (swap! presences disj pn-channel)
  nil)

(defn here-now
  "Returns all clients currently subscribed to the channel."
  [pn-channel]
  (let [res            (pubnub-get (here-now-request pn-channel))
        {:keys [body]} res]
    (json/parse-string body true)))

;;; Utilities

(defn time
  "Returns the PubNub (Long) time value or an exception message.

   This has not functional value other than a PING to the PubNub Cloud."
  [pn-channel]
  (try
    (let [res            (pubnub-get (time-request pn-channel))
          {:keys [body]} res]
      (first (json/parse-string body true)))
    (catch IOException ioe
      (error-message ioe))
    (catch ExceptionInfo ei
      (let [body (get-in (.getData ei) [:object :body])
            m    (json/parse-string body true)]
        (error-message m)))
    (catch Exception e
      e)))

(defn uuid
  "Returns random, unique UUID string."
  []
  (str (UUID/randomUUID)))
