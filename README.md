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

[![Build Status](https://travis-ci.org/sstone/akka-amqp-proxies.png)](https://travis-ci.org/sstone/akka-amqp-proxies)

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
    <version>1.3</version>
  </dependency>
  <dependency>
    <groupId>com.typesafe.akka</groupId>
    <artifactId>akka-actor_SCALA-VERSION</artifactId>
    <version>AKKA-VERSION</version>
  </dependency>
</dependencies>
```

From version 1.1X on, snapshots are published to https://oss.sonatype.org/content/repositories/snapshots/ and releases
are synced to Maven Central

* version 1.1 (master branch) is compatible with Scala 2.9.2 and Akka 2.0.5
* version 1.1 (scala2.10 branch) is compatible with Scala 2.10 and Akka 2.1.0
* version 1.3 (scala2.10 branch) is compatible with Scala 2.10.X and Akka 2.1.X
* version 1.4 (scala2.10 branch) targets Scala 2.10.X and Akka 2.2.X

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

## Calling actors from other languages (python, ruby, ...)

AMQP defines a binary protocol, and there are many AMQP clients available (PHP, python, ruby, java, scala, C#, haskell, ....)
If you choose a standard message format, you can easily call Akka actors though AMQP proxies. 
So, let's see how we would call our "calculator" actor from other languages. The pattern is very simple and based on "standard" AMQP RPC:
* create an exclusive, auto-delete reply queue (usually with a random broker-generated name)
* publish serialized AddRequest messages with the following properties
	* reply-to = name of the reply queue
	* content-type = message type (the name of the class of the message we're sending, com.github.sstone.amqp.proxy.Demo$AddRequest in our case)
	* content-encoding = message format (json in our case)
* wait for a response, which should be an AddResponse(x, y, sum) message in JSON format

To run the client samples, don't forget to start the server with mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass=com.github.sstone.amqp.proxy.Server

### From python (using [pika](https://github.com/pika/pika))

```python
import pika
import uuid
import json

class CalculatorRpcClient(object):
    def __init__(self):
        self.connection = pika.BlockingConnection(pika.ConnectionParameters(host='localhost'))

        self.channel = self.connection.channel()

        result = self.channel.queue_declare(exclusive=True)
        self.callback_queue = result.method.queue

        self.channel.basic_consume(self.on_response, no_ack=True, queue=self.callback_queue)

    def on_response(self, ch, method, props, body):
        if self.corr_id == props.correlation_id:
            self.response = body

    def add(self, x, y):
        self.response = None
        self.corr_id = str(uuid.uuid4())
        msg = json.dumps({ "x":x, "y":y })
        self.channel.basic_publish(exchange='',
                                   routing_key='calculator',
                                   properties=pika.BasicProperties(
                                         reply_to = self.callback_queue,
                                         correlation_id = self.corr_id,
										 content_type = 'com.github.sstone.amqp.proxy.Demo$AddRequest',
										 content_encoding = 'json'
                                         ),
                                   body=msg)
        while self.response is None:
            self.connection.process_data_events()
        raw = self.response
        decoded = json.loads(raw)
        return decoded['sum']

calculator_rpc = CalculatorRpcClient()

x = 1
y = 2
print " [x] Requesting ", x, " + ", y
response = calculator_rpc.add(x, y)
print " [.] Got %r" % (response,)
```

### From ruby (using [ruby-amqp](https://github.com/ruby-amqp/amqp))

```ruby
require 'amqp'
require 'json'
require 'securerandom'

x = 1
y = 2

EventMachine.run do
  connection = AMQP.connect(:host => '127.0.0.1')
  puts "Connecting to AMQP broker. Running #{AMQP::VERSION} version of the gem..."

  channel = AMQP::Channel.new(connection)
  exchange = channel.default_exchange

  # create a JSON AddRequest message
  request = JSON.generate({:x => x, :y => y})

  # "standard" RPC pattern: create an exclusive private queue with a unique, randomized, broker-generated name
  # and publish message with the 'reply-to' property set to the name of this reply queue
  reply_queue = channel.queue("", :exclusive => true, :auto_delete => true) do |queue|
    exchange.publish request, {
        :routing_key => "calculator",
        :correlation_id => SecureRandom.uuid,
        :reply_to => queue.name,
        :content_encoding => "json",
        :content_type => "com.github.sstone.amqp.proxy.Demo$AddRequest"
    }
  end
  reply_queue.subscribe do |metadata, payload|
    puts "raw esponse for #{metadata.correlation_id}: #{metadata.content_encoding} #{metadata.content_type} #{payload.inspect}"

    # payload shoud look like { "x" : x, "y" : y, "sum" : sum }
    response = JSON.parse(payload)
    puts "#{x} + #{y} = #{response['sum']}"

    connection.close {
      EventMachine.stop { exit }
    }
  end

end
```
