package com.aphelia.amqp.proxy.serializers

import org.xerial.snappy.Snappy
import akka.serialization.Serializer

abstract class SnappySerializer(serializer: Serializer) extends Serializer {

  def identifier = 4

  def includeManifest = true

  def toBinary(o: AnyRef) = {
    val bytes = serializer.toBinary(o)
    val zipped = Snappy.compress(bytes)
    zipped
  }

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val unzipped = Snappy.uncompress(bytes)
    serializer.fromBinary(unzipped, manifest)
  }

}

//object SnappyJsonSerializer extends SnappySerializer(JsonSerializer)

object SnappyProtobufSerializer extends SnappySerializer(ProtobufSerializer)

object SnappyThriftSerializer extends SnappySerializer(ThriftSerializer)

