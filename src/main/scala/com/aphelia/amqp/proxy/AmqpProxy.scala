package com.aphelia.amqp.proxy


import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.serialization.Serializer
import com.aphelia.amqp.{RpcClient, RpcServer}
import com.aphelia.amqp.RpcServer.ProcessResult
import com.rabbitmq.client.AMQP.BasicProperties
import com.aphelia.amqp.Amqp.{Publish, Delivery}
import akka.dispatch.{Future, Await}
import akka.util.Timeout
import akka.pattern.ask
import akka.util.duration._
import grizzled.slf4j.Logging


object AmqpProxy {

  case class Failure(error: Int, reason: String)

  class ProxyServer(server: ActorRef, serializer: Serializer, timeout:Timeout = 30 seconds) extends RpcServer.IProcessor with Logging {

    def makeResult[T <: AnyRef](t: T)(implicit mt: Manifest[T]) = ProcessResult(
      Some(serializer.toBinary(t)),
      Some(new BasicProperties.Builder().contentType(mt.erasure.getName).build())
    )

    def process(delivery: Delivery) = {
      trace("consumer %s received %s with properties %s".format(delivery.consumerTag, delivery.envelope, delivery.properties))
      debug("received message of size %dB".format(delivery.body.length))
      val request =  serializer.fromBinary(delivery.body, Some(Class.forName(delivery.properties.getContentType)))
      debug("handling delivery of type %s".format(request.getClass.getName))
      val future = (server ? request)(timeout).mapTo[AnyRef]
      val response = Await.result(future, timeout.duration)
      val clazz = response.getClass
      debug("received response of type %s".format(clazz.getName))
      val body = serializer.toBinary(response)
      debug("sending response of size %dB".format(body.length))
      ProcessResult(Some(body), Some(new BasicProperties.Builder().contentType(clazz.getName).build()))
    }

    def onFailure(delivery: Delivery, e: Exception) = makeResult(Failure(1, e.toString))
  }

  class ProxyClient(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer, timeout:Timeout = 30 seconds) extends Actor {
    protected def receive = {
      case msg: AnyRef => {
        val contentType = msg.getClass.getName
        val body = serializer.toBinary(msg)
        val properties = Some(new BasicProperties.Builder().contentType(contentType).build)
        val publish = Publish(exchange, routingKey, body, properties, mandatory = true, immediate = false)
        val future: Future[RpcClient.Response] = (client ? RpcClient.Request(publish :: Nil, 1))(timeout).mapTo[RpcClient.Response]
        val dest = sender
        future.onComplete {
          case Right(result) => {
            val delivery = result.deliveries(0)
            val response = serializer.fromBinary(delivery.body, Some(Class.forName(delivery.properties.getContentType)))
            dest ! response
          }
          case Left(error) => dest ! akka.actor.Status.Failure(error)
        }
      }
    }
  }
}
