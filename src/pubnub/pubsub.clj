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
(ns pubnub.pubsub
  "Please move on, nothing to see here.

   These are just implementation details."
  (:refer-clojure :exclude [time])
  (:require [clojure.core.async :refer [>! <! >!! <!! chan close! go go-loop pub sub sliding-buffer]]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [digest :as digest]
            [pubnub.crypto :as crypto]
            [pubnub.common :as common])
  (:import [clojure.lang ExceptionInfo]
           [java.io IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identites

(def ^{:private true} subscriptions (atom #{}))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

(defn subscribed?
  "Returns true if (PubNub) channel is currently subscribed."
  [pn-channel]
  (contains? @subscriptions pn-channel))

(defn publish
  [{:keys [encrypt-cipher] :as pn-channel} message]
  (try
    (let [enc-msg (if encrypt-cipher (crypto/encrypt pn-channel (json/generate-string message)) message)
          msg     (json/generate-string enc-msg)]
      (common/pubnub-get (publish-request pn-channel msg))
      {:status :ok :message "Sent"})
    (catch IOException ioe
      (common/error-message ioe))
    (catch ExceptionInfo ei
      (let [body (get-in (.getData ei) [:object :body])
            m    (json/parse-string body true)]
        (common/error-message m)))
    (catch Exception e
      e)))

(defn- subscribe*
  "Subscribe to PubNub channel."
  [{:keys [encrypt-cipher] :as pn-channel}]
  (let [c (chan)]
    (swap! subscriptions conj pn-channel)
    (try (go-loop [timetoken 0]
           (let [{:keys [body]}       (common/pubnub-get (`subscribe-request pn-channel timetoken))
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
                 (close! c)))))
         (catch IOException ioe
           (>! c (common/error-message ioe)))
         (catch ExceptionInfo ei
           (let [body (get-in (.getData ei) [:object :body])
                 m    (json/parse-string body true)]
             (>! c (common/error-message m))))
         (catch Exception e
           (>! c {:status :error :message "ERROR!"}))
         (finally
           (swap! subscriptions disj pn-channel)
           (close! c)))
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
