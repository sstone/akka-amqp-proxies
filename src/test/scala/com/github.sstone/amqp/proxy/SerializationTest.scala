package com.github.sstone.amqp.proxy

import gpbtest.Gpbtest
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import serializers._
import thrifttest.Person
import com.rabbitmq.client.AMQP.BasicProperties

case class Message(a: String, b: Int)

class SerializationTest extends AssertionsForJUnit {

  @Test def verifyJsonSerialization() {

    val serializer = JsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = AmqpProxy.serialize(msg, serializer)

    assert(new String(body) === """{"a":"toto","b":123}""")
    assert(props.getContentEncoding === "json")
    assert(props.getContentType === "com.github.sstone.amqp.proxy.Message")

    val (deserialized, _) = AmqpProxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyJsonSerialization() {

    val serializer = SnappyJsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = AmqpProxy.serialize(msg, serializer)

    assert(props.getContentEncoding === "snappy-json")
    assert(props.getContentType === "com.github.sstone.amqp.proxy.Message")

    val (deserialized, _) = AmqpProxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyProtobufSerialization() {

    val serializer = ProtobufSerializer
    val msg = Gpbtest.Person.newBuilder().setId(123).setName("toto").setEmail("a@b.com").build

    val (body, props) = AmqpProxy.serialize(msg, serializer)

    assert(props.getContentEncoding === "protobuf")
    assert(props.getContentType === """com.github.sstone.amqp.proxy.gpbtest.Gpbtest$Person""")

    val (deserialized, _) = AmqpProxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyProtobufSerialization() {

    val serializer = SnappyProtobufSerializer
    val msg = Gpbtest.Person.newBuilder().setId(123).setName("toto").setEmail("a@b.com").build

    val (body, props) = AmqpProxy.serialize(msg, serializer)

    assert(props.getContentEncoding === "snappy-protobuf")
    assert(props.getContentType === """com.github.sstone.amqp.proxy.gpbtest.Gpbtest$Person""")

    val (deserialized, _) = AmqpProxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyThriftSerialization() {

    val serializer = ThriftSerializer
    val msg = new Person().setId(123).setName("toto").setEmail("a@b.com")

    val (body, props) = AmqpProxy.serialize(msg, serializer)

    assert(props.getContentEncoding === "thrift")
    assert(props.getContentType === """com.github.sstone.amqp.proxy.thrifttest.Person""")

    val (deserialized, _) = AmqpProxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyThriftSerialization() {

    val serializer = SnappyThriftSerializer
    val msg = new Person().setId(123).setName("toto").setEmail("a@b.com")

    val (body, props) = AmqpProxy.serialize(msg, serializer)

    assert(props.getContentEncoding === "snappy-thrift")
    assert(props.getContentType === """com.github.sstone.amqp.proxy.thrifttest.Person""")

    val (deserialized, _) = AmqpProxy.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifDefaultSerialization() {
    val json = """{"a":"toto","b":123}"""
    val msg = Message("toto", 123)
    val props = new BasicProperties.Builder().contentType("com.github.sstone.amqp.proxy.Message").build
    val (deserialized, serializer) = AmqpProxy.deserialize(json.getBytes("UTF-8"), props)
    assert(deserialized === msg)
    assert(serializer === JsonSerializer)
  }
}