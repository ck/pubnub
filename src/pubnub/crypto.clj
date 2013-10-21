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

(ns pubnub.crypto
  "Cryptographic functions to help keeping things secure.

   Uses Bouncy Castle Crypto, a lightweight cryptography API."
  (:require [clojure.string :as str]
            [digest :as digest])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.io InputStream OutputStream]
           [org.bouncycastle.crypto.engines AESEngine]
           [org.bouncycastle.crypto.modes CBCBlockCipher]
           [org.bouncycastle.crypto.paddings PaddedBufferedBlockCipher]
           [org.bouncycastle.crypto.params KeyParameter ParametersWithIV]
           [org.bouncycastle.util.encoders Base64]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Identities

(def ^{:const true :private true} default-initialization-vector "0123456789012345")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; API

(defn make-ciphers
  "Initialize the ciphers for encryption."
  [{:keys [cipher-key initialization-vector]
    :or   {initialization-vector default-initialization-vector}}]
  (when cipher-key
    (let [key            (-> (digest/sha-256 cipher-key)
                             (.getBytes "UTF-8")
                             (String.)
                             (.substring 0 32)
                             (.toLowerCase))
          parameter-iv   (ParametersWithIV. (KeyParameter. (.getBytes key "UTF-8"))
                                            (.getBytes initialization-vector "UTF-8"))
          encrypt-cipher (PaddedBufferedBlockCipher. (CBCBlockCipher. (AESEngine.)))
          decrypt-cipher (PaddedBufferedBlockCipher. (CBCBlockCipher. (AESEngine.)))
          _              (.init encrypt-cipher true parameter-iv)
          _              (.init decrypt-cipher false parameter-iv)]
      {:encrypt-cipher encrypt-cipher
       :decrypt-cipher decrypt-cipher})))

(defn encrypt
  "Encodes the encrypted message using the cipher-key."
  [{:keys [encrypt-cipher]} ^String message]
  (let [in     (.getBytes message)
        in-len (count in)
        out    (byte-array (.getOutputSize encrypt-cipher in-len))
        len    (.processBytes encrypt-cipher in 0 in-len out 0)
        _      (.doFinal encrypt-cipher out len)]
    (String. (Base64/encode out))))

(defn decrypt
  "Decodes the encrypted message using the cipher-key."
  [{:keys [decrypt-cipher]} ^String message]
  (let [in     (Base64/decode message)
        in-len (count in)
        out    (byte-array (.getOutputSize decrypt-cipher in-len))
        len    (.processBytes decrypt-cipher in 0 in-len out 0)
        _      (.doFinal decrypt-cipher out len)]
    (clojure.string/replace (String. out) #"(\x00)+$" "")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Scratchpad

(comment

  ;; used cipher
  (def cipher (make-ciphers {:cipher-key "my secret cipher key"}))

  (pprint (encrypt cipher "hello"))
  ;;=> "7djE+qHCsyk0zm95C2v1gg==" is expected result!!!!

  (decrypt cipher "7djE+qHCsyk0zm95C2v1gg==")
  ;;=> "hello"

)
