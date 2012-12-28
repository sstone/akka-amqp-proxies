# Akka AMQP Proxies

## Overview

“AMQP proxies” is a simple way of integrating AMQP with Akka to distribute jobs across a network of computing nodes.
You still write “local” code, have very little to configure, and end up with a distributed, elastic,
fault-tolerant grid where computing nodes can be written in nearly every programming language.

This project started as a demo for [http://letitcrash.com/post/29988753572/akka-amqp-proxies](http://letitcrash.com/post/29988753572/akka-amqp-proxies)

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
    <groupId>com.aphelia</groupId>
    <artifactId>akka-amqp-proxies_SCALA-VERSION</artifactId>
    <version>1.1-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-actor_SCALA-VERSION</artifactId>
    <version>AKKA-VERSION</version>
  </dependency>
</dependencies>
```

* version 1.0-SNAPSHOT is compatible with Scala 2.9.2 and Akka 2.0.3
* version 1.1-SNAPSHOT (master branch) is compatible with Scala 2.9.2 and Akka 2.0.3
* version 1.1-SNAPSHOT (scala2.10 branch) is compatible with Scala 2.10 and Akka 2.1.0

## Calculator demo

The demo is simple:

* start with write a basic local calculator actor that can add numbers
* "proxify" it over AMQP, using protobuf serialization
* you can now start as many calculator servers as you want, and you get an elastic, fault-tolerant, load-balanced calculator grid

I've defined a simple protobuf command language, generated java sources and added them to the project:

```protobuf
package com.aphelia.amqp.proxy.calculator;

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
  required int32 sum = 1;
}
```

Of course, in a real project, you would generate java sources at compilation time (using either one the of protobuf maven plugins or just the ant plugin).
It should be fairly simple to write compatible sample clients and servers with Python or Ruby to demonstrate interroperrability

To start the demo with local actors:

* mvn exec:java -Dexec.mainClass=com.aphelia.amqp.proxy.Local

To start the demo with a client proxy and remote server actors:

* mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass=com.github.sstone.amqp.proxy.Server (as many times as you want)
* mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass=com.github.sstone.amqp.proxy.Client

## About JSON serialization

I've temporarily commented out JSON serialization code in the scala2.10 branch. Most libraries are not compatible with 2.10
yet, and it seems that new scala features will open up new possibilities for serialization libraries. I'll restore it as soon
as I know which option to choose from (feel free to suggest one, or better still: send a pull request :-))

