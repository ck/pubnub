(defproject pubnub "0.1.0-SNAPSHOT"
  :description "Clojure PubNub Client"

  :url "http://github.com/ck/pubnub"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [cheshire "5.3.1"]
                 [clj-http-lite "0.2.0"]
                 [digest "1.4.3"]
                 [org.bouncycastle/bcprov-jdk16 "1.46"]]

  :deploy-repositories {"releases"  {:url "https://clojars.org/repo/" :creds :gpg}
                        "snapshots" {:url "https://clojars.org/repo/" :creds :gpg}}

  :profiles {:dev          {:source       [["dev"]]
                            :dependencies [[expectations "1.4.56"]]}
             :1.5          {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6          {:dependencies [[org.clojure/clojure "1.6.0-alpha1"]]}
             :sanity-check {:aot                :all
                            :warn-on-reflection true
                            :compile-path       "target/sanity-check-aot"}}
  :aliases  {"sanity-check" ["do" "clean," "with-profile" "sanity-check" "compile"]}

  :warn-on-reflection false
  :min-lein-version "2.0.0")
