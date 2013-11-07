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
(ns pubnub-test
  (:refer-clojure :exclude [time])
  (:require [expectations :refer :all]
            [clj-http.lite.client :as http]
            [pubnub :refer :all]))

;;; --- fixtures ------------------------------------

(declare test-channel-conf)

(defn load-test-config
  "Loads test configuration."
  {:expectations-options :before-run}
  []
  (alter-var-root #'test-channel-conf
                  (constantly (read-string (slurp "./test/config.clj")))))

;;; --- channel ------------------------------------

;; default origin to pubsub.pubnub.com
(expect {:origin "pubsub.pubnub.com"}
  (in (channel {})))

;; enable ssl by default
(expect {:ssl? true}
  (in (channel {})))

;; create random client-id if none given
(expect #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
  (:client-id (channel {})))

;;; --- publish ------------------------------------

;; successful send String
(expect-let [channel (channel test-channel-conf)]
  {:status :ok, :message "Sent"}
  (publish channel "Test message 1"))

;; successful send map
(expect-let [channel (channel test-channel-conf)]
  {:status :ok, :message "Sent"}
  (publish channel {:foo "bar" :baz 42}))

;; successful send vector
(expect-let [channel (channel test-channel-conf)]
  {:status :ok, :message "Sent"}
  (publish channel [:foo "bar" :baz 42]))

;; failed send returns error
(expect-let [invalid-conf (assoc test-channel-conf :publish-key "pub-c-87654321-1111-2222-3333-210987654321")
             channel      (channel invalid-conf)]
  {:status :error, :message "Invalid Key"}
  (publish channel "Test message 2"))

;;; --- subscribed? ------------------------------------

;; is unsubscribed by default
(expect-let [channel (channel test-channel-conf)]
  false?
  (subscribed? channel))

;;; --- subscribe ------------------------------------

(expect-let [channel    (channel test-channel-conf)
             _          (subscribe channel)
             subscribed (subscribed? channel)
             _          (unsubscribe channel)]
  true?
  subscribed)

;;; --- unsubscribe ------------------------------------

(expect-let [channel    (channel test-channel-conf)
             _          (subscribe channel)
             _          (unsubscribe channel)
             subscribed (subscribed? channel)]
  false?
  subscribed)

;;; --- presence-unsubscribe ------------------------------------

;;; --- here-now ------------------------------------

;;; --- time ------------------------------------

;; successful call returns Long time value
(expect-let [channel (channel test-channel-conf)]
  Long
  (time channel))

(expect-let [channel (channel test-channel-conf)]
  true?
  (> (time channel) 13000000000000000))

;;; --- uuid ------------------------------------

;; create uuid
(expect #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
  (uuid))

;; uuids are different
(expect-let [uuid-1 (uuid)
             uuid-2 (uuid)]
  false? (= uuid-1 uuid-2))
