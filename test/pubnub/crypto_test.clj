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
(ns pubnub.crypto-test
  (:require [expectations :refer :all]
            [pubnub.crypto :refer :all]))

;;; --- fixtures ------------------------------------

(def cipher (make-ciphers {:cipher-key "secret test cipher key"}))

;;; --- encrypt & decrypt  --------------------------

;; single word
(expect "soBqymNEHUotV2YDpSmTtQ=="
  (encrypt cipher "hello"))

(expect "hello"
  (decrypt cipher "soBqymNEHUotV2YDpSmTtQ=="))

;; preserve leading and trailing spaces
(expect " hello there "
  (->> " hello there "
       (encrypt cipher)
       (decrypt cipher)))

;; handle embedded strings
(expect "\"Hello World!\""
  (->> "\"Hello World!\""
       (encrypt cipher)
       (decrypt cipher)))

;; handle embedded data structure characters
(expect "[ {:foo 1, :bar \"baz\"} 3.1415]"
  (->> "[ {:foo 1, :bar \"baz\"} 3.1415]"
       (encrypt cipher)
       (decrypt cipher)))

;; same message for different cipher-keys result in different outcomes

(expect-let [other-cipher (make-ciphers {:cipher-key "another secret test cipher key"})]
  false? (= (encrypt cipher "hello")
            (encrypt other-cipher "hello")))

;; same message for same cipher-keys with different
;; inititialization-vectors result in different outcomes
(expect-let [c1 (make-ciphers {:cipher-key "my secret cipher key"
                               :initialization-vector "0123456789012345"})
             c2 (make-ciphers {:cipher-key "my secret cipher key"
                               :initialization-vector "9999999999999999"})]
  false? (= (encrypt c1 "hello")
            (encrypt c2 "hello")))
