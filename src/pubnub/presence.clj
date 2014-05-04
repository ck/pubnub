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
(ns pubnub.presence
  "Please move on, nothing to see here.

   These are just implementation details."
  (:refer-clojure :exclude [time])
  (:require [clojure.core.async :refer [>! <! >!! <!! chan close! go go-loop pub sub sliding-buffer]]
            [cheshire.core :as json]
            [pubnub.common :as common]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identites

(def ^{:const true :private true} presence-channel-suffix "-pnpres")
(def ^{:private true} presences (atom #{}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

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
             (let [{:keys [body]}         (common/pubnub-get (presence-request pn-channel timetoken))
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

   - :success
   - :error

   and hooks up the callback-fn to the ::success topic
   and the error-fn to the ::error topic (via two channels).

   In order for the channels not to block, the publication
   uses a sliding-buffer of 10 elements.

   The publication is based on the channel supplied by presence*."
  [pn-channel success-fn error-fn]
  (when-not (presence? pn-channel)
    (let [msgs-ch     (presence* pn-channel)
          topic-fn    (fn [msg] :status)
          buffer-fn   (fn [topic] (sliding-buffer 10))
          publication (pub msgs-ch topic-fn buffer-fn)
          success-ch  (chan)
          error-ch    (chan)
          _           (sub publication :success success-ch false)
          _           (sub publication :error error-ch false)]
      (go (while (presence? pn-channel) (success-fn (<! success-ch))))
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
  (let [res            (common/pubnub-get (here-now-request pn-channel))
        {:keys [body]} res]
    (json/parse-string body true)))
