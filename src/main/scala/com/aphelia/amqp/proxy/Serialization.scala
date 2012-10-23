package com.aphelia.amqp.proxy

import com.rabbitmq.client.AMQP.BasicProperties
import akka.serialization.Serializer
import serializers.{SnappyJsonSerializer, JsonSerializer, ThriftSerializer, ProtobufSerializer}

object Serialization {

  val serializersMap = Map("json" -> JsonSerializer, "snappy-json" -> SnappyJsonSerializer, "protobuf" -> ProtobufSerializer, "thrift" -> ThriftSerializer)

  def getSerializer(contentEncoding: String) = serializersMap.getOrElse(contentEncoding, JsonSerializer)

  def getSerializerName(s: Serializer) = serializersMap.map(_.swap).get(s).get

  def deserialize(body: Array[Byte], props: BasicProperties) = {
    val serializer = getSerializer(props.getContentEncoding)
    val clazz = Some(Class.forName(props.getContentType))
    serializer.fromBinary(body, clazz)
  }

  def serialize[T <: AnyRef](msg: T, serializer: Serializer): (Array[Byte], BasicProperties) = {
    (serializer.toBinary(msg), new BasicProperties.Builder().contentEncoding(getSerializerName(serializer)).contentType(msg.getClass.getName).build)
  }

  def serialize[T <: AnyRef](msg: T, serializerName: String): (Array[Byte], BasicProperties) = serialize(msg, getSerializer(serializerName))

}
