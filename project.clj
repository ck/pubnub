(defproject pubnub "0.4.0"
  :description "Clojure PubNub Client"
  :url "http://github.com/ck/pubnub"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [prismatic/schema "0.2.2"]
                 [cheshire "5.3.1"]
                 [slingshot "0.10.3"]
                 [clj-http "0.9.1"]
                 [digest "1.4.4"]
                 [org.bouncycastle/bcprov-jdk16 "1.46"]]

  :signing             {:gpg-key "DEDC8F15"}
  :deploy-repositories [["clojars" {:creds :gpg}]]

  :profiles {:dev          {:source       [["dev"]]
                            :dependencies [[org.clojure/test.check "0.5.8"]]}
             :1.5          {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6          {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :sanity-check {:aot          :all
                            :global-vars  {*warn-on-reflection* true}
                            :compile-path "target/sanity-check-aot"}}
  :aliases  {"sanity-check" ["do" "clean," "with-profile" "sanity-check" "compile"]}

  :global-vars  {*warn-on-reflection* false}
  :min-lein-version "2.2.0")
