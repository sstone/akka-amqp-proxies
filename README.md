Akka AMQP Proxies
=================

“AMQP proxies” is a simple way of integrating AMQP with Akka to distribute jobs across a network of computing nodes.
You still write “local” code, have very little to configure, and end up with a distributed, elastic,
fault-tolerant grid where computing nodes can be written in nearly every programming language.

Read more at [http://letitcrash.com/post/29988753572/akka-amqp-proxies](http://letitcrash.com/post/29988753572/akka-amqp-proxies)

The original code has been improved, with the addition of on-the-fly compression and a protobuf serializer (thanks to PM47!)

To start the demo with local actors:

* java -cp akka-amqp-proxies-1.0-SNAPSHOT-shaded.jar com.aphelia.amqp.proxy.Local

To start the demo with a client proxy and remote server actors:

* java -cp akka-amqp-proxies-1.0-SNAPSHOT-shaded.jar com.aphelia.amqp.proxy.Server  (as many times as you want)
* java -cp akka-amqp-proxies-1.0-SNAPSHOT-shaded.jar com.aphelia.amqp.proxy.Client

