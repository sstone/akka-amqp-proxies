package com.aphelia.amqp.proxy

import gpbtest.Gpbtest
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import serializers.{SnappyJsonSerializer, JsonSerializer, ProtobufSerializer}

case class Message(a: String, b: Int)

class SerializationTest extends AssertionsForJUnit {

  @Test def verifyJsonSerialization() {

    val serializer = JsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = Serialization.serialize(msg, serializer)

    assert(new String(body) === """{"a":"toto","b":123}""")
    assert(props.getContentEncoding === "json")
    assert(props.getContentType === "com.aphelia.amqp.proxy.Message")

    val deserialized = Serialization.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifySnappyJsonSerialization() {

    val serializer = SnappyJsonSerializer
    val msg = Message("toto", 123)

    val (body, props) = Serialization.serialize(msg, serializer)

    assert(props.getContentEncoding === "snappy-json")
    assert(props.getContentType === "com.aphelia.amqp.proxy.Message")

    val deserialized = Serialization.deserialize(body, props)

    assert(deserialized === msg)
  }

  @Test def verifyProtobufSerialization() {

    val serializer = ProtobufSerializer
    val msg = Gpbtest.Person.newBuilder().setId(123).setName("toto").setEmail("a@b.com").build

    val (body, props) = Serialization.serialize(msg, serializer)

    assert(props.getContentEncoding === "protobuf")
    assert(props.getContentType === """com.aphelia.amqp.proxy.gpbtest.Gpbtest$Person""")

    val deserialized = Serialization.deserialize(body, props)

    assert(deserialized === msg)
  }
}