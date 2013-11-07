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
(ns pubnub.common
  "Please move on, nothing to see here.

   These are just implementation details."
  (:require [clj-http.lite.client :as http])
  (:import [clojure.lang LazySeq PersistentArrayMap]
           [java.io IOException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identites

(def ^{:const true :private true} headers
  {"V"          "3.0"
   "User-Agent" "Clojure"
   "Accept"     "*/*"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Errors

(defmulti error-message type)

(defmethod error-message IOException [e]
  {:status :error :message (.getMessage e)})

(defmethod error-message PersistentArrayMap [e]
  {:status :error :message (:message e)})

(defmethod error-message LazySeq [e]
  {:status :error :message (second e)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Request

(defn pubnub-get
  [req]
  (http/get req {:headers headers}))
