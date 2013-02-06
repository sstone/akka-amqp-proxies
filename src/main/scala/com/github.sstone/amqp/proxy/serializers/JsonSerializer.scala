package com.github.sstone.amqp.proxy.serializers

import akka.serialization.Serializer
import net.liftweb.json._
import net.liftweb.json.Serialization.{read, write}

object JsonSerializer extends Serializer {
  implicit val formats = Serialization.formats(NoTypeHints)

  def identifier = 123456789

  def includeManifest = true

  def toBinary(o: AnyRef) = write(o).getBytes("UTF-8")

  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    require(manifest.isDefined)
    val string = new String(bytes)
    implicit val mf = Manifest.classType(manifest.get)
    read(string)
  }
}

