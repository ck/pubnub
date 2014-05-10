; Copyright 2013-2014 Christian Kebekus
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
  (:require [slingshot.slingshot :refer (try+)]
            [clojure.core.async :as async]
            [cheshire.core :as json]
            [pubnub.common :as common])
  (:import [java.io IOException]
           [java.net UnknownHostException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identites

(def ^{:const true :private true} presence-channel-suffix "-pnpres")
(def ^{:private true} presences (atom {}))

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

(defn- leave-request
  [{:keys [subscribe-key channel origin client-id ssl?]}]
  (let [protocol (if ssl? "https" "http")]
    (format "%s://%s/v2/presence/sub-key/%s/channel/%s%s/leave?uuid=%s"
            protocol
            origin
            subscribe-key
            channel
            presence-channel-suffix
            client-id)))

(defn- leave
  "Send leave request for presence channel."
  [pn-channel]
  (let [res            (common/pubnub-get (leave-request pn-channel))
        {:keys [body]} res]
    (json/parse-string body true)))

(defn- listen
  "Listen for presence events since the timetoken on the PubNub channel."
  [pn-channel timetoken]
  (try+
   {:value (common/pubnub-get (presence-request pn-channel timetoken))}
   (catch UnknownHostException uhe
     {:exception uhe :retry false})
   (catch IOException ioe
     {:exception ioe :retry true})
   (catch (and (map? %) (contains? % :status)) m
     {:exception m   :retry false})
   (catch Exception e
     {:exception e   :retry false})))

(declare presence?)

(defn- presence-subscribe*
  "Listen on the PubNub channel."
  [pn-channel]
  (let [c (async/chan (async/sliding-buffer (pn-channel :buffer-size)))]
    (swap! presences assoc pn-channel c)
    (async/go
      (loop [timetoken 0]
        (let [result (listen pn-channel timetoken)]
          (when (presence? pn-channel)
            (if-let [e (:exception result)]
              (if (:retry result)
                (do
                  (async/<! (async/timeout 3000))
                  (recur timetoken))
                (do
                  (async/>! c (common/error-message e))
                  (async/close! c)
                  (swap! presences dissoc pn-channel)))
              (let [body                   (get-in result [:value :body])
                    [events new-timetoken] (json/parse-string body true)]
                ;; TODO: Events results are
                ;; [{action join, timestamp 1381776169, uuid f8edab21-18c7-49c4-8436-62ea1af67673, occupancy 1}]
                ;; [{action leave, timestamp 1381776169, uuid f8edab21-18c7-49c4-8436-62ea1af67673, occupancy 0}]
                ;; [{action timeout, timestamp 1381776535, uuid f8edab21-18c7-49c4-8436-62ea1af67673, occupancy 1}]
                ;; Should be converted to {:action "join" :client-id "f8edab21-18c7-49c4-8436-62ea1af67673"}
                (doseq [event events]
                  (async/>! c {:status :success, :event event}))
                (recur new-timetoken)))))))
    c))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

(defn presence?
  "Returns true if (PubNub) channel is currently subscribed."
  [pn-channel]
  (contains? @presences pn-channel))

(defn presence-subscribe
  "Subscribe to the PubNub channel.
  Returns the core.async channel."
  [pn-channel]
  (if (presence? pn-channel)
    (@presences pn-channel)
    (presence-subscribe* pn-channel)))

(defn presence-unsubscribe
  "Unsubscribe from the presence channel."
  [pn-channel]
  (let [c (@presences pn-channel)]
    (leave pn-channel)
    (async/close! c)
    (swap! @presences dissoc pn-channel)
    nil))

(defn here-now
  "Returns all clients currently subscribed to the channel."
  [pn-channel]
  (let [res            (common/pubnub-get (here-now-request pn-channel))
        {:keys [body]} res]
    (json/parse-string body true)))
