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
(ns pubnub
  "API for Pubnub."
  (:refer-clojure :exclude [time])
  (:require [schema.core :as s]
            [schema.macros :as sm]
            [pubnub.crypto :as crypto]
            [pubnub.pubsub :as pubsub]
            [pubnub.presence :as presence]
            [pubnub.util :as util])
  (:import [org.bouncycastle.crypto.paddings PaddedBufferedBlockCipher]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas and Schema Helpers

;;; Helpers

(defn- valid-uuid? [uuid]
  (re-matches #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
              uuid))

(defn- valid-subscribe-key? [key]
  (re-matches #"^sub-c-[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}$"
              key))

(defn- valid-publish-key? [key]
  (re-matches #"^pub-c-[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}$"
              key))

(defn- valid-secret-key? [key]
  (re-matches #"^sec-c-[A-Za-z0-9]{48}$"
              key))

;;; Schemas

(def UUID
  (s/both s/Str
          (s/pred valid-uuid? 'valid-uuid?)))

(def PubNubSubscribeKey
  (s/both s/Str
          (s/pred valid-subscribe-key? 'valid-subscribe-key?)))

(def PubNubPublishKey
  (s/both s/Str
          (s/pred valid-publish-key? 'valid-publish-key?)))

(def PubNubSecretKey
  (s/both s/Str
          (s/pred valid-secret-key? 'valid-secret-key?)))

(def PubNubEncoding
  (s/enum :edn :json))

(def PubNubChannelConf
  {:channel                      s/Str
   (s/optional-key :client-id)   s/Str
   (s/optional-key :origin)      s/Str
   :subscribe-key                PubNubSubscribeKey
   (s/optional-key :publish-key) PubNubPublishKey
   (s/optional-key :secret-key)  PubNubSecretKey
   (s/optional-key :cipher-key)  (s/maybe s/Str)
   (s/optional-key :ssl?)        s/Bool
   (s/optional-key :encoding)    PubNubEncoding
   (s/optional-key :buffer-size) s/Int})

(def PubNubChannel
  (merge PubNubChannelConf
         {(s/optional-key :encrypt-cipher) PaddedBufferedBlockCipher
          (s/optional-key :decrypt-cipher) PaddedBufferedBlockCipher}))

(def PublishResult
  (s/either {:status (s/eq :success), :message (s/eq "Sent")}
            {:status (s/eq :error),   :message s/Str}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constructors

(sm/defn ^:always-validate channel :- PubNubChannel
  "Create PubNub channel from the passed in configuration.

   {:channel       \"my-channel\"
    :client-id     \"my-pubnub-client\"
    :origin        \"my-company.pubnub.com\"
    :subscribe-key \"sub-c-12345678-1111-2222-3333-123456789012\"
    :publish-key   \"pub-c-87654321-1111-2222-3333-210987654321\"
    :secret-key    \"sec-c-1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789012\"
    :cipher-key    \"my super secret cipher key\"
    :ssl?          false
    :encoding      :edn
    :buffer-size   10}

   If ::origin, :ssl?, :client-id, or :encoding are not specified,
   they default to

   - :origin    \"pubsub.pubnub.com\"
   - :ssl?      true
   - :client-id <generated uuid>
   - :encoding  :json

   It is recommended to

   - use a custom origin, e.g. <company>.pubnub.com
   - only provide subscribe-key for public apps
   - keep channel name secure, e.g. via obscurity by applying a SHA-256

  For more information see PubNub Best Practices (http://bit.ly/GX6JFG)."
  [conf :- PubNubChannelConf]
  (merge {:origin      "pubsub.pubnub.com"
          :ssl?        true
          :client-id   (util/uuid)
          :encoding    :json
          :buffer-size 10}
         conf
         (crypto/make-ciphers conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; PubSub

(sm/defn publish :- PublishResult
  "Publish a message to the PubNub channel.

  Returns {:status :success, :message \"Sent\"} if message was send successfully.
  Otherwise it returns {:status :error, :message <error message>}"
  [pn-channel :- PubNubChannel message]
  (pubsub/publish pn-channel message))

(sm/defn subscribed? :- s/Bool
  "Returns true if (PubNub) channel is currently subscribed,
  otherwise false."
  [pn-channel :- PubNubChannel]
  (pubsub/subscribed? pn-channel))

(sm/defn subscribe
  "Subscribe to the PubNub channel.
  Returns a (core.async) channel."
  [pn-channel :- PubNubChannel]
  (pubsub/subscribe pn-channel))

(sm/defn unsubscribe
  "Unsubscribe from the PubNub channel.
  Closes (core.sync) channel at the end."
  [pn-channel :- PubNubChannel]
  (pubsub/unsubscribe pn-channel))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Presence

(sm/defn presence-subscribe
  "Subscribe to presence events for the PubNub channel."
  [pn-channel :- PubNubChannel]
  (presence/presence-subscribe pn-channel))

(sm/defn presence-unsubscribe
  "Unsubscribe to presence events for the PubNub channel."
  [pn-channel :- PubNubChannel]
  (presence/presence-unsubscribe pn-channel))

(sm/defn here-now
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
  [pn-channel :- PubNubChannel]
  (presence/here-now pn-channel))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(sm/defn time
  "Returns the PubNub (Long) time value or an exception message.

   Examples:

     (time my-channel)
     ;;=> 13833327151957688

   This has not functional value other than a PING to the PubNub Cloud."
  [pn-channel :- PubNubChannel]
  (util/time pn-channel))

(defn uuid
  "Returns random, unique UUID string."
  []
  (util/uuid))
