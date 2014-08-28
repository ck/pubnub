# PubNub #

A Clojure [PubNub][pubnub] Client

## Installation ##

Pubnub is available in Clojars. Add this `:dependency` to your Leiningen
`project.clj`

```clojure
[pubnub "0.4.0"]
```

Or, add this to your Maven project's `pom.xml`

```xml
<repository>
  <id>clojars</id>
  <url>http://clojars.org/repo</url>
</repository>

<dependency>
  <groupId>pubnub</groupId>
  <artifactId>pubnub</artifactId>
  <version>0.4.0</version>
</dependency>
```

Pubnub is compatible with Clojure 1.5.1+.


## Usage ##

To get started, first we need to register a free [PubNub account][pubnub-account].
This will provide us with a PubNub Sandbox we can play in:


| Name          | Key                                                    |
|---------------|--------------------------------------------------------|
| Subscribe Key	| sub-c-12345678-1111-2222-3333-123456789012             |
| Publish Key   | pub-c-87654321-1111-2222-3333-210987654321             |
| Secret Key    | sec-c-1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789012 |


With the keys we can define a configuration

```clojure
(def test-channel-conf
  {:channel       "test-channel"
   :client-id     "test-pubnub-client"
   :origin        "test.pubnub.com"
   :subscribe-key "sub-c-12345678-1111-2222-3333-123456789012"
   :publish-key   "pub-c-87654321-1111-2222-3333-210987654321"
   :secret-key    "sec-c-1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789012"})
```

to create a PubNub channel

```clojure
(def test-channel (channel test-channel-conf))
```

With the channel at hand, we can now publish messages to that channel:

```clojure
(publish test-channel "Hello World!")
(publish test-channel {:number 419 :ts (java.util.Date.)})
```

To receive any message published to the channel, we need
to subscribe to it. The subscription returns a Clojure [core.async][core.async]
channel that will contain any new message.

```clojure
(subscribed? test-channel)
(def subscription (subscribe test-channel))
(subscribed? test-channel)
```

Retrieve and process messages using any of the operations
core.async provides. E.g.

```clojure
(async/thread
  (loop []
    (when-let [msg (async/<!! subscription)]
      (println msg)
      (recur)))
  (println "Unsubscribed"))
```

When we do not want to receive any additonal messages,
simply unsubscribe:

```clojure
(pn/unsubscribe test-channel)
(pn/subscribed? test-channel)
```

It is important to note that *only* messages posted to the channel
*after* the client subscribed to it are received. I.e. the client
will not receive any historic messages.


## Best Practices ##

The client is built around [PubNub's Best Practices][pubnub-best-practices].

### Use SSL ###

By default the connection uses SSL. To disable it, simply set `ssl?`
to `false` in the configutation

```clojure
(def test-channel-conf
  {:channel "test-channel"
   ;; ...
   :ssl?    false})
```

### Client UUID ###

If no client-id is provided in the configuration, a UUID client-id is
generated. The generated UUID will change everytime the channel is
created, even with eveything else being the same.


## ToDo ##

* Hookup presence
* Support EDN as data format
* Handle PubNub errors for publish
    * "Message Too Big" - Max message size exceeded.
    * "Invalid Publish Key" - Wrong Publish Key was Used.
    * "Invalid Message Signature" - The message was SHA256 Signed incorrectly.
* Support connect/disconnect/reconnect events
* Add timeouts to subscribe/presence
* Write documentation
* Support proxy server (server, port)
* Look into using persistent connections (https://github.com/dakrone/clj-http#using-persistent-connections)


## License ##

    Copyright © 2013-2014 Christian Kebekus

    The use and distribution terms for this software are covered by the
    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
    which can be found in the file [epl-v10.html](epl-v10.html) at the
    root of this distribution.

    By using this software in any fashion, you are agreeing to be bound by
    the terms of this license.

    You must not remove this notice, or any other, from this software.


[core.async]: http://github.com/clojure/core.async
[pubnub]: http://www.pubnub.com
[pubnub-account]: http://www.pubnub.com/account
[pubnub-best-practices]: http://bit.ly/GX6JFG
