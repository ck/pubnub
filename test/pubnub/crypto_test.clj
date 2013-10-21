(ns pubnub.crypto-test
  (:require [expectations :refer :all]
            [pubnub.crypto :refer :all]))

;;; --- fixtures ------------------------------------

(def cipher (make-ciphers {:cipher-key "my secret cipher key"}))

;;; --- encrypt & decrypt  --------------------------

;; single word
(expect "7djE+qHCsyk0zm95C2v1gg=="
  (encrypt cipher "hello"))

(expect "hello"
  (decrypt cipher "7djE+qHCsyk0zm95C2v1gg=="))

;; ;; preserve leading and trailing spaces
;; (expect " hello there "
;;   (->> " hello there "
;;        (encrypt cipher)
;;        (decrypt cipher)))

;; ;; handle embedded strings
;; (expect "\"Hello World!\""
;;   (->> "\"Hello World!\""
;;        (encrypt cipher)
;;        (decrypt cipher)))

;; ;; handle embedded data structure characters
;; (expect "[ {:foo 1, :bar \"baz\"} 3.1415]"
;;   (->> "[ {:foo 1, :bar \"baz\"} 3.1415]"
;;        (encrypt cipher)
;;        (decrypt cipher)))

;; ;; same message for different cipher-keys result in different outcomes

;; (expect-let [other-cipher (make-ciphers {:cipher-key "another secret cipher key"})]
;;   false? (= (encrypt cipher "hello")
;;             (encrypt other-cipher "hello")))

;; ;; same message for same cipher-keys with different
;; ;; inititialization-vectors result in different outcomes
;; (expect-let [ch1 (make-ciphers {:cipher-key "my secret cipher key"
;;                                 :initialization-vector "init-vector-1"})
;;              ch2 (make-ciphers {:cipher-key "my secret cipher key"
;;                                 :initialization-vector "init-vector-2"})]
;;   false? (= (encrypt ch1 "hello")
;;             (encrypt ch2 "hello")))
