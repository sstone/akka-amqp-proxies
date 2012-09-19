package com.aphelia.amqp.proxy

import akka.actor.{Actor, ActorRef}
import akka.serialization.Serializer
import com.aphelia.amqp.{RpcClient, RpcServer}
import com.aphelia.amqp.RpcServer.ProcessResult
import com.aphelia.amqp.proxy.Serialization._
import com.rabbitmq.client.AMQP.BasicProperties
import com.aphelia.amqp.Amqp.{Publish, Delivery}
import akka.dispatch.{Future, Await}
import akka.util.Timeout
import akka.pattern.ask
import akka.util.duration._
import grizzled.slf4j.Logging
import com.aphelia.serializers.{SnappyJsonSerializer, JsonSerializer}
import com.rabbitmq.client.AMQP


object AmqpProxy {

  case class Failure(error: Int, reason: String)

  class ProxyServer(server: ActorRef, timeout: Timeout = 30 seconds) extends RpcServer.IProcessor with Logging {

    def makeResult(message: Tuple2[Array[Byte], AMQP.BasicProperties]) = ProcessResult(
      Some(message._1),
      Some(message._2)
    )

    def process(delivery: Delivery) = {
      trace("consumer %s received %s with properties %s".format(delivery.consumerTag, delivery.envelope, delivery.properties))
      val request = deserialize(delivery.body, delivery.properties)
      debug("handling delivery of type %s".format(request.getClass.getName))
      val future = (server ? request)(timeout).mapTo[AnyRef]
      val response = Await.result(future, timeout.duration)
      debug("sending response of type %s".format(response.getClass.getName))
      makeResult(serialize(response, delivery.properties.getContentEncoding)) // we answer with the same encoding type
    }

    def onFailure(delivery: Delivery, e: Exception) = makeResult(serialize(Failure(1, e.toString), delivery.properties.getContentEncoding))
  }

  class ProxyClient(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer, timeout: Timeout = 30 seconds) extends Actor {

    protected def receive = {
      case msg: AnyRef => {
        val serialized = serialize(msg, serializer)
        val publish = Publish(exchange, routingKey, serialized._1, Some(serialized._2), mandatory = true, immediate = false)
        val future: Future[RpcClient.Response] = (client ? RpcClient.Request(publish :: Nil, 1))(timeout).mapTo[RpcClient.Response]
        val dest = sender
        future.onComplete {
          case Right(result) => {
            val delivery = result.deliveries(0)
            val response = deserialize(delivery.body, delivery.properties)
            dest ! response
          }
          case Left(error) => dest ! akka.actor.Status.Failure(error)
        }
      }
    }
  }

}
