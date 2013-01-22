package com.github.sstone.amqp.proxy.serializers

import org.xerial.snappy.Snappy
import akka.serialization.Serializer
import grizzled.slf4j.Logging

abstract class SnappySerializer(serializer: Serializer) extends Serializer with Logging {

  def identifier = 4

  def includeManifest = true

  def toBinary(o: AnyRef) = {
    val bytes = serializer.toBinary(o)
    val zipped = Snappy.compress(bytes)
    debug("zipped %dB to %dB".format(bytes.length, zipped.length))
    zipped
  }

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val unzipped = Snappy.uncompress(bytes)
    debug("unzipped %dB to %dB".format(bytes.length, unzipped.length))
    serializer.fromBinary(unzipped, manifest)
  }

}

object SnappyJsonSerializer extends SnappySerializer(JsonSerializer)

object SnappyProtobufSerializer extends SnappySerializer(ProtobufSerializer)

object SnappyThriftSerializer extends SnappySerializer(ThriftSerializer)

