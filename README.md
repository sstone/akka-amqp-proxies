Akka AMQP Proxies
=================

“AMQP proxies” is a simple way of integrating AMQP with Akka to distribute jobs across a network of computing nodes.
You still write “local” code, have very little to configure, and end up with a distributed, elastic,
fault-tolerant grid where computing nodes can be written in nearly every programming language.

To start the demo with local actors:

> java -cp akka-amqp-proxies-1.0-SNAPSHOT-shaded.jar com.aphelia.Local

To start the demo with a client proxy and remote server actors:

> java -cp akka-amqp-proxies-1.0-SNAPSHOT-shaded.jar com.aphelia.Server

> java -cp akka-amqp-proxies-1.0-SNAPSHOT-shaded.jar com.aphelia.Client

