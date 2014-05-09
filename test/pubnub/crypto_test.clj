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
(ns pubnub.crypto-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as ct :refer (defspec)]
            [schema.test :as st]
            [pubnub.crypto :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Fixtures

(def cipher (make-ciphers {:cipher-key "secret test cipher key"}))

(use-fixtures :once st/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Example-based Tests

(deftest test-encrypt-and-decrypt

  (is (= "soBqymNEHUotV2YDpSmTtQ=="
         (encrypt cipher "hello"))
      "encrypt single word")

  (is (= "hello"
         (decrypt cipher "soBqymNEHUotV2YDpSmTtQ=="))
      "decrypt single word")

  (is (= "[ {:foo 1, :bar \"baz\"} 3.1415]"
         (->> "[ {:foo 1, :bar \"baz\"} 3.1415]"
              (encrypt cipher)
              (decrypt cipher)))
      "handle embedded data structure characters")

  (let [other-cipher (make-ciphers {:cipher-key "another secret test cipher key"})]
    (is (not= (encrypt cipher "hello")
              (encrypt other-cipher "hello"))
        "same message for different cipher-keys result in different outcomes"))

  (let [c1 (make-ciphers {:cipher-key "my secret cipher key"
                          :initialization-vector "0123456789012345"})
        c2 (make-ciphers {:cipher-key "my secret cipher key"
                          :initialization-vector "9999999999999999"})]
    (is (not= (encrypt c1 "hello")
              (encrypt c2 "hello"))
        "same message for same cipher-keys with different inititialization-vectors result in different outcomes"))
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Property-based Tests

(defspec encrypt-decrypt-roundtrip
  1000 ;; iterations
  (prop/for-all [s gen/string-ascii]
                (= s (decrypt cipher (encrypt cipher s)))))
