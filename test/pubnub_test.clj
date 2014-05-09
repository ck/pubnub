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
(ns pubnub-test
  (:refer-clojure :exclude [time])
  (:require [clojure.test :refer :all]
            [schema.test :as st]
            [clj-http.client :as http]
            [pubnub :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures

(declare test-channel-conf)

(defn load-test-config-fixture [f]
  (alter-var-root #'test-channel-conf
                  (constantly (read-string (slurp "./test/config.clj"))))
  (f))

(use-fixtures :once
  load-test-config-fixture
  st/validate-schemas)

(def dummy-conf {:channel       "1"
                 :subscribe-key "sub-c-12345678-1111-2222-3333-123456789012"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Tests

(deftest test-channel

  (is (= "pubsub.pubnub.com"
         (:origin (channel dummy-conf)))
      "default origin to pubsub.pubnub.com")

  (is (true? (:ssl? (channel dummy-conf)))
      "enable ssl by default")

  (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                  (:client-id (channel dummy-conf)))
      "create random client-id if none given"))

(deftest test-publish

  (let [channel (channel test-channel-conf)]
    (is (= {:status :success, :message "Sent"}
           (publish channel "Test message 1"))
        "successful send String"))

  (let [channel (channel test-channel-conf)]
    (is (= {:status :success, :message "Sent"}
           (publish channel {:foo "bar" :baz 42}))
        "successful send map"))

  (let [channel (channel test-channel-conf)]
    (is (= {:status :success, :message "Sent"}
           (publish channel [:foo "bar" :baz 42]))
        "successful send vector"))

  (let [invalid-conf (assoc test-channel-conf :publish-key "pub-c-87654321-1111-2222-3333-210987654321")
        channel      (channel invalid-conf)]
    (is (= {:status :error, :message "Invalid Key"}
           (publish channel "Test message 2"))
        "failed send returns error")))

(deftest test-subscribed?

  (let [channel (channel test-channel-conf)]
    (is (false?
         (subscribed? channel))
        "is unsubscribed by default")))

;; (deftest test-subscribe

;;   (let [channel    (channel test-channel-conf)
;;         _          (subscribe channel)
;;         _          (Thread/sleep 2000)
;;         subscribed (subscribed? channel)
;;         _          (unsubscribe channel)]
;;     (is (true? subscribed))))

;; (deftest test-unsubscribe

;;   (let [channel    (channel test-channel-conf)
;;         _          (subscribe channel)
;;         _          (unsubscribe channel)
;;         subscribed (subscribed? channel)]
;;     (is (false? subscribed))) )

(deftest test-presence-unsubscribe
  )

(deftest test-here-now
  )

(deftest test-time

  (let [channel (channel test-channel-conf)]
    (is (instance? Long
                   (time channel))
        "successful call returns Long time value"))

  (let [channel (channel test-channel-conf)]
    (is (true?
         (> (time channel) 13000000000000000)))))

(deftest test-uuid

  (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                  (uuid))
      "create uuid")

  (let [uuid-1 (uuid)
        uuid-2 (uuid)]
    (is (not= uuid-1 uuid-2)
        "uuids are different")))
