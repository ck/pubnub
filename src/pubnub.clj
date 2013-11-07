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
(ns pubnub
  "API for Pubnub."
  (:refer-clojure :exclude [time])
  (:require [pubnub.crypto :as crypto]
            [pubnub.pubsub :as pubsub]
            [pubnub.presence :as presence]
            [pubnub.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constructors

(defn channel
  "Create PubNub channel from the passed in configuration.

   {:channel       \"my-channel\"
    :client-id     \"my-pubnub-client\"
    :origin        \"my-company.pubnub.com\"
    :subscribe-key \"sub-c-12345678-1111-2222-3333-123456789012\"
    :publish-key   \"pub-c-87654321-1111-2222-3333-210987654321\"
    :secret-key    \"sec-c-1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789012\"
    :cipher-key    \"my super secret cipher key\"
    :ssl?          false
    :encoding      :edn}

   If ::origin, :ssl?, :client-id, or :encoding are not specified,
   they default to

   - :origin    \"pubsub.pubnub.com\"
   - :ssl?      true
   - :client-id <generated uuid>
   - :encoding  :json

   It is recommended to

   - use a custom origin, e.g. <company>.pubnub.com
   - only provide subscribe-key for public apps
   - keep channel name secure via obscurity, e.g. apply SHA-256

  For more information see PubNub Best Practices (http://bit.ly/GX6JFG)."
  [conf]
  (merge {:origin    "pubsub.pubnub.com"
          :ssl?      true
          :client-id (util/uuid)
          :encoding  :json}
         conf
         (crypto/make-ciphers conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; PubSub

(defn publish

  "Publish a message to the PubNub channel.

   Returns {:status :ok, :message \"Sent\"} if message was send successfully.
   Otherwise it returns {:status :error, :message <error message>}
  "
  [pn-channel message]
  (pubsub/publish pn-channel message))

(defn subscribed?
  "Returns true if (PubNub) channel is currently subscribed."
  [pn-channel]
  (pubsub/subscribed? pn-channel))

(defn subscribe
  "Subscribe to the PubNub channel."
  [pn-channel
   & {:keys [callback error]
      :or   {callback (constantly nil)
             error    (constantly nil)}}]
  (pubsub/subscribe pn-channel callback error))

(defn unsubscribe
  "Unsubscribe from the PubNub channel.
   Closes (core.sync) channel at the end."
  [pn-channel]
  (pubsub/unsubscribe pn-channel))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Presence

(defn presence
  "Subscribe to presence events for the PubNub channel."
  [pn-channel
   & {:keys [callback error]
      :or   {callback (constantly nil)
             error    (constantly nil)}}]
  (presence/presence pn-channel callback error))

(defn presence-unsubscribe
  "Unsubscribe to presence events for the PubNub channel."
  [pn-channel]
  (presence/presence-unsubscribe pn-channel))

(defn here-now
  "Returns the current occupancy status of the channel.

   Note that there is a delay between the time a client
   unsubscribes from the PubNub channel and the time
   the client is reported unsubscribed by this function.

   Examples:

   - Channel with no subscribers

     (here-now my-channel)
     ;;=> {\"uuids\"   []
           \"occupancy 0}

   - Channel with two subscribers:

     (here-now my-channel)
     ;;=> {\"uuids\"   [\"f8edab21-18c7-49c4-8436-62ea1af67673\"
                        \"my-pubnub-test-client\"]
           \"occupancy 2}

  "
  [pn-channel]
  (presence/here-now pn-channel))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(defn time
  "Returns the PubNub (Long) time value or an exception message.

   Examples:

     (time my-channel)
     ;;=> 13833327151957688

   This has not functional value other than a PING to the PubNub Cloud."
  [pn-channel]
  (util/time pn-channel))

(defn uuid
  "Returns random, unique UUID string."
  []
  (util/uuid))
