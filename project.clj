(defproject pubnub "0.1.5"
  :description "Clojure PubNub Client"
  :url "http://github.com/ck/pubnub"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.301.0-deb34a-alpha"]
                 [cheshire "5.3.1"]
                 [clj-http-lite "0.2.0"]
                 [digest "1.4.4"]
                 [org.bouncycastle/bcprov-jdk16 "1.46"]]

  :signing             {:gpg-key "DEDC8F15"}
  :deploy-repositories [["clojars" {:creds :gpg}]]

  :profiles {:dev          {:source       [["dev"]]}
             :1.5          {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6          {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :sanity-check {:aot                :all
                            :warn-on-reflection true
                            :compile-path       "target/sanity-check-aot"}}
  :aliases  {"sanity-check" ["do" "clean," "with-profile" "sanity-check" "compile"]}

  :warn-on-reflection false
  :min-lein-version "2.0.0")
