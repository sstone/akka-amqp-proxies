# Akka AMQP Proxies

## Overview

“AMQP proxies” is a simple way of integrating AMQP with Akka to distribute jobs across a network of computing nodes.
You still write “local” code, have very little to configure, and end up with a distributed, elastic,
fault-tolerant grid where computing nodes can be written in nearly every programming language.

This project started as a demo for [http://letitcrash.com/post/29988753572/akka-amqp-proxies](http://letitcrash.com/post/29988753572/akka-amqp-proxies) and
is now used in a few "real" projects.

The original code has been improved, with the addition of:
* on-the-fly compression with snappy
* a protobuf serializer (thanks to PM47!)
* a thrift serializer

## Configuring maven/sbt

```xml
 <repositories>
    <repository>
        <id>sonatype snapshots</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
    </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.sstone</groupId>
    <artifactId>akka-amqp-proxies_SCALA-VERSION</artifactId>
    <version>1.1</version>
  </dependency>
  <dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-actor_SCALA-VERSION</artifactId>
    <version>AKKA-VERSION</version>
  </dependency>
</dependencies>
```

From version 1.1X on, snapshots are published to https://oss.sonatype.org/content/repositories/snapshots/ and releases
are synced to Maven Central. The latest snapshot version is 1.2-SNAPSHOT, the latest released version is 1.1

* version 1.1 (master branch) is compatible with Scala 2.9.2 and Akka 2.0.5
* version 1.1 (scala2.10 branch) is compatible with Scala 2.10 and Akka 2.1.0

## Calculator demo

The demo is simple:

* start with write a basic local calculator actor that can add numbers
* "proxify" it over AMQP, using JSON serialization
* you can now start as many calculator servers as you want, and you get an elastic, fault-tolerant, load-balanced calculator grid

To start the demo with local actors:

* mvn exec:java -Dexec.mainClass=com.github.sstone.amqp.proxy.Local

To start the demo with a client proxy and remote server actors:

* mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass=com.github.sstone.amqp.proxy.Server (as many times as you want)
* mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass=com.github.sstone.amqp.proxy.Client

## Using Protobuf/Thrift serialization

Please check [https://github.com/sstone/akka-amqp-proxies/blob/scala2.10/src/test/scala/com/github.sstone/amqp/proxy/RemoteGpbCallTest.scala] for an example
of how to use Protobuf: I've defined a simple protobuf command language, generated java sources and added them to the project:

``` protobuf
package com.github.sstone.amqp.proxy.calculator;

// simple calculator API

// add request
//
message AddRequest {
  required int32 x = 1;
  required int32 y = 2;
}

// Add response
//
message AddResponse {
  required int32 x = 1;
  required int32 y = 2;
  required int32 sum = 3;
}
```

Now I can exchange AddRequest and AddResponse messages transparently:

```scala
proxy ? AddRequest.newBuilder.setX(x).setY(y).build()).mapTo[AddResponse]
```

You would follow the same steps to use Thrift instead of Protobuf.
Of course, in a real project, you would generate java sources at compilation time (using either one the of protobuf maven plugins or just the ant plugin).
It should be fairly simple to write compatible sample clients and servers with Python or Ruby to demonstrate interroperrability


