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
(ns pubnub.common
  "Please move on, nothing to see here.

   These are just implementation details."
  (:require [slingshot.slingshot :refer (try+ throw+)]
            [cheshire.core :as json]
            [clj-http.client :as http])
  (:import [clojure.lang ExceptionInfo PersistentArrayMap]
           [java.io IOException]
           [java.net UnknownHostException]))

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
  {:status  :error
   :message (.getMessage e)})

(defmethod error-message UnknownHostException [e]
  {:status  :error
   :message (.getMessage e)})

(defmethod error-message ExceptionInfo [e]
  {:status  :error
   :message "INFO ERROR!"})

(defmethod error-message Exception [e]
  {:status  :error
   :message "ERROR!"})

(defmethod error-message PersistentArrayMap [m]
  (let [body (m :body)
        e    (json/parse-string body true)]
    {:status  :error
     :message (or (:message e) (second e))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Request

(defn pubnub-get [req]
  (http/get req {:headers        headers
                 ;; :socket-timeout 1000
                 ;; :conn-timeout   1000
                 :retry-handler  (constantly false)}))
