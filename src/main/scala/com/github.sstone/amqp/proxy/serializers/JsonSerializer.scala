package com.github.sstone.amqp.proxy.serializers

import akka.serialization.Serializer
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization

object JsonSerializer extends Serializer {
  implicit val formats = Serialization.formats(NoTypeHints)

  def identifier = 123456789

  def includeManifest = true

  def toBinary(o: AnyRef) = Serialization.write(o).getBytes("UTF-8")

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    require(manifest.isDefined)
    val string = new String(bytes)
    implicit val mf = Manifest.classType(manifest.get)
    Serialization.read(string)
  }
}

