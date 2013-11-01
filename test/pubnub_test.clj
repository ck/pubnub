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

(def test-channel-conf
  {:channel       "test-channel"
   :client-id     "test-pubnub-client"
   :origin        "test.pubnub.com"
   :subscribe-key "sub-c-12345678-1111-2222-3333-123456789012"
   :publish-key   "pub-c-87654321-1111-2222-3333-210987654321"
   :secret-key    "sec-c-1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789012"})

(def test-channel (channel test-channel-conf))

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

;; successful send
(expect {:status :ok, :message "Sent"}
  (with-redefs [http/get (constantly {:headers {} :status 200 :body "[1,\"Sent\",\"13817572494159376\"]"})]
    (publish test-channel "Hello")))

;; failed send returns err
(expect Exception
  (with-redefs [http/get (constantly (throw (Exception. "an error happened")))]
    (publish test-channel "Hello")))

;;; --- subscribed? ------------------------------------

;;; --- subscribe ------------------------------------

;;; --- unsubscribe ------------------------------------

;;; --- presence-unsubscribe ------------------------------------

;;; --- here-now ------------------------------------

;;; --- time ------------------------------------

;; successful call returns time
(expect 13817572494159376
  (with-redefs [http/get (constantly {:headers {} :status 200 :body "[13817572494159376]"})]
    (time test-channel)))

;; failed call returns error

;;; --- uuid ------------------------------------

;; create uuid
(expect #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
  (uuid))

;; uuids are different
(expect-let [uuid-1 (uuid)
             uuid-2 (uuid)]
  false? (= uuid-1 uuid-2))
