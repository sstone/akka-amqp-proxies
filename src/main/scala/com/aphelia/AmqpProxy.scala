package com.aphelia

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.serialization.Serializer
import amqp.Amqp.{Publish, Delivery}
import amqp.{RpcClient, RpcServer}
import amqp.RpcServer.ProcessResult
import com.rabbitmq.client.AMQP.BasicProperties
import akka.dispatch.{Future, Await}
import akka.util.Timeout
import akka.util.duration._

object AmqpProxy {

  case class Failure(error: Int, reason: String)

  class ProxyServer(server: ActorRef, serializer: Serializer) extends RpcServer.IProcessor {
    def makeResult[T <: AnyRef](t: T)(implicit mt: Manifest[T]) = ProcessResult(
      Some(serializer.toBinary(t)),
      Some(new BasicProperties.Builder().contentType(mt.erasure.getName).build())
    )

    def process(delivery: Delivery) = {
      val request =  serializer.fromBinary(delivery.body, Some(Class.forName(delivery.properties.getContentType)))
      val future = (server ? request)(10 seconds).mapTo[AnyRef]
      val response = Await.result(future, 10 seconds)
      val clazz = response.getClass
      val body = serializer.toBinary(response)
      ProcessResult(Some(body), Some(new BasicProperties.Builder().contentType(clazz.getName).build()))
    }

    def onFailure(delivery: Delivery, e: Exception) = makeResult(Failure(1, e.toString))
  }

  class ProxyClient(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer) extends Actor {
    protected def receive = {
      case msg: AnyRef => {
        val contentType = msg.getClass.getName
        val body = serializer.toBinary(msg)
        val timeout: Timeout = 10 second
        val publish = Publish(exchange, routingKey, body, properties = Some(new BasicProperties.Builder().contentType(contentType).build), mandatory = true, immediate = false)
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

