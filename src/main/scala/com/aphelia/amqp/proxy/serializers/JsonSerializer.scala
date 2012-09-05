package com.aphelia.serializers

import akka.serialization.Serializer
import com.codahale.jerkson.Json

object JsonSerializer extends Serializer {

  def identifier = 123456789

  def includeManifest = true

  def toBinary(o: AnyRef) = Json.generate(o).getBytes

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    Json.parse(string)(Manifest.classType(manifest.get))
  }
}
