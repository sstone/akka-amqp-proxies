package com.aphelia.amqp.proxy

import com.aphelia.amqp.proxy.serializers.{SnappyJsonSerializer, JsonSerializer, ProtobufSerializer}
import com.rabbitmq.client.AMQP.BasicProperties
import akka.serialization.Serializer

/**
 * Serialization of messages to be sent over AMQP
 * We use the following convention for mapping serialization info to AMQP properties:
 * <ul>
 *   <li>content-type is used to store the message class name (for example "com.foo.MyRequest")</li>
 *   <li>content-encoding is used to store the serialization format (for example: "json", "snappy-json" or "protobuf"</li>
 * </ul>
 */
object Serialization {

  val serializersMap = Map("json" -> JsonSerializer, "snappy-json" -> SnappyJsonSerializer, "protobuf" -> ProtobufSerializer)

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
