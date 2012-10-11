package com.aphelia.amqp.proxy.serializers

import akka.serialization.Serializer
import com.codahale.jerkson.Json
import org.xerial.snappy.Snappy
import grizzled.slf4j.Logging

object SnappyJsonSerializer extends Serializer with Logging {

  def identifier = 987654321

  def includeManifest = true

  def toBinary(o: AnyRef) = {
    val bytes = Json.generate(o).getBytes
    val zipped = Snappy.compress(bytes)
    debug("zipped %dB to %dB".format(bytes.length, zipped.length))
    zipped
  }

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val unzipped = Snappy.uncompress(bytes)
    debug("unzipped %dB to %dB".format(bytes.length, unzipped.length))
    val string = new String(unzipped)
    Json.parse(string)(Manifest.classType(manifest.get))
  }
}
