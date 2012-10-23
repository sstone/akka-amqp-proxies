package com.aphelia.amqp.proxy.serializers

import org.apache.thrift.{TFieldIdEnum, TDeserializer, TBase, TSerializer}
import akka.serialization.Serializer


object ThriftSerializer extends Serializer {

  def includeManifest: Boolean = true
  def identifier = 3

  val serializer = new TSerializer()
  val deserializer = new TDeserializer()

  def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: TBase[_, _] ⇒ serializer.serialize(m)
    case _ ⇒ throw new IllegalArgumentException("Can't serialize a non-thrift message using thrift [" + obj + "]")
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef =
    clazz match {
      case None ⇒ throw new IllegalArgumentException("Need a thrift message class to be able to deserialize bytes using thrift")
      case Some(c) ⇒ {
        val o = c.getConstructor().newInstance().asInstanceOf[TBase[_ <: TBase[_, _], _ <: TFieldIdEnum]]
        deserializer.deserialize(o, bytes)
        o
      }
    }
}
