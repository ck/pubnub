(ns pubnub-test
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

;; disable ssl by default
(expect {:ssl? false}
  (in (channel {})))

;; create random client-id if none given
(expect #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
  (:client-id (channel {})))

;;; --- publish ------------------------------------

;; return [1 "Sent"] on success
(expect [1 "Sent"]
  (with-redefs [http/get (constantly {:headers {} :status 200 :body [1,"Sent","13817572494159376"]})]
    (publish test-channel "Hello")))

;; ;; return [0 <error message>] on failure
;; (expect [0 "an error happened"]
;;   (with-redefs [http/get (constantly (throw (Exception. "an error happened")))]
;;     (publish test-channel "Hello")))


;;; --- subscribed? ------------------------------------

;;; --- subscribe ------------------------------------

b;;; --- unsubscribe ------------------------------------
