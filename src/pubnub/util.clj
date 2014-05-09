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
(ns pubnub.util
  "Please move on, nothing to see here.

   These are just implementation details."
  (:refer-clojure :exclude [time])
  (:require [cheshire.core :as json]
            [pubnub.common :as common])
  (:import [clojure.lang ExceptionInfo]
           [java.io IOException]
           [java.util UUID]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn- time-request
  [{:keys [origin ssl?]}]
  (let [protocol (if ssl? "https" "http")]
    (format "%s://%s/time/0"
            protocol
            origin)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

(defn time
  "Returns the PubNub (Long) time value or an exception message.

   This has not functional value other than a PING to the PubNub Cloud."
  [pn-channel]
  (try
    (let [res            (common/pubnub-get (time-request pn-channel))
          {:keys [body]} res]
      (first (json/parse-string body true)))
    (catch IOException ioe
      (common/error-message ioe))
    (catch ExceptionInfo ei
      (let [body (get-in (.getData ei) [:object :body])
            m    (json/parse-string body true)]
        (common/error-message m)))
    (catch Exception e
      e)))

(defn uuid
  "Returns random, unique UUID string."
  []
  (str (UUID/randomUUID)))
