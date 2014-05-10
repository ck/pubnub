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
(ns pubnub.pubsub
  "Please move on, nothing to see here.

   These are just implementation details."
  (:refer-clojure :exclude [time])
  (:require [slingshot.slingshot :refer (try+)]
            [clojure.core.async :as async]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [digest :as digest]
            [pubnub.crypto :as crypto]
            [pubnub.common :as common])
  (:import [java.io IOException]
           [java.net UnknownHostException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identites

(def ^{:private true} subscriptions (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn- create-signature
  [{:keys [publish-key subscribe-key secret-key channel]}]
  (digest/md5 (format "%s/%s/%s/%s"
                      publish-key
                      subscribe-key
                      secret-key
                      channel)))

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

(defn- leave-request
  [{:keys [subscribe-key channel origin client-id ssl?]}]
  (let [protocol (if ssl? "https" "http")]
    (format "%s://%s/v2/presence/sub-key/%s/channel/%s/leave?uuid=%s"
            protocol
            origin
            subscribe-key
            channel
            client-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(defn- leave
  "Send leave request for channel."
  [pn-channel]
  (let [res            (common/pubnub-get (leave-request pn-channel))
        {:keys [body]} res]
    (json/parse-string body true)))

(defn- decode
  "Decode the data."
  [{:keys [encrypt-cipher] :as pn-channel} data]
  (if encrypt-cipher
    (mapv #(json/parse-string (crypto/decrypt pn-channel %) true) data)
    data))

(defn- listen
  "Listen for data since the timetoken on the PubNub channel."
  [pn-channel timetoken]
  (try+
   {:value (common/pubnub-get (subscribe-request pn-channel timetoken))}
   (catch UnknownHostException uhe
     {:exception uhe :retry false})
   (catch IOException ioe
     {:exception ioe :retry true})
   (catch (and (map? %) (contains? % :status)) m
     {:exception m   :retry false})
   (catch Exception e
     {:exception e   :retry false})))

(declare subscribed?)

(defn- subscribe*
  [pn-channel]
  (let [c (async/chan (async/sliding-buffer (pn-channel :buffer-size)))]
    (swap! subscriptions assoc pn-channel c)
    (async/go
      (loop [timetoken 0]
        (let [result (listen pn-channel timetoken)]
          (when (subscribed? pn-channel)
            (if-let [e (:exception result)]
              (if (:retry result)
                (do
                  (async/<! (async/timeout 3000))
                  (recur timetoken))
                (do
                  (async/>! c (common/error-message e))
                  (async/close! c)
                  (swap! subscriptions dissoc pn-channel)))
              (let [body                 (get-in result [:value :body])
                    [data new-timetoken] (json/parse-string body true)
                    msgs                 (decode pn-channel data)]
                (doseq [msg msgs]
                  (async/>! c {:status :success, :payload msg}))
                (recur new-timetoken)))))))
    c))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

(defn subscribed?
  "Returns true if (PubNub) channel is currently subscribed."
  [pn-channel]
  (contains? @subscriptions pn-channel))

(defn publish
  [{:keys [encrypt-cipher] :as pn-channel} message]
  (try+
   (let [enc-msg (if encrypt-cipher
                   (crypto/encrypt pn-channel (json/generate-string message))
                   message)
         msg     (json/generate-string enc-msg)]
     (common/pubnub-get (publish-request pn-channel msg))
     {:status :success, :message "Sent"})
   (catch UnknownHostException uhe
     (common/error-message uhe))
   (catch IOException ioe
     (common/error-message ioe))
   (catch (and (map? %) (contains? % :status)) m
     (common/error-message m))
   (catch Exception e
     (common/error-message e))))

(defn subscribe
  "Subscribe to the PubNub channel.
  Returns the core.async channel."
  [pn-channel]
  (if (subscribed? pn-channel)
    (@subscriptions pn-channel)
    (subscribe* pn-channel)))

(defn unsubscribe
  "Unsubscribe from the channel."
  [pn-channel]
  (let [c (@subscriptions pn-channel)]
    (leave pn-channel)
    (async/close! c)
    (swap! subscriptions dissoc pn-channel)
    nil))
