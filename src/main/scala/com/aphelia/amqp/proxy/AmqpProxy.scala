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

  /**
   * standard  one-request/one response proxy, which allows to write (myActor ? MyRequest).mapTo[MyResponse]
   * @param client AMQP RPC Client
   * @param exchange exchange to which requests will be sent
   * @param routingKey routing key with which requests will be sent
   * @param serializer message serializer
   * @param timeout response time-out
   * @param mandatory AMQP mandatory flag used to sent requests with; default to true
   * @param immediate AMQP immediate flag used to sent requests with; default to false; use with caution !!
   * @param deliveryMode AMQP delivery mode to sent request with; defaults to 1 (
   */
  class ProxyClient(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer, timeout: Timeout = 30 seconds, mandatory: Boolean = true, immediate: Boolean = false, deliveryMode: Int = 1) extends Actor {

    protected def receive = {
      case msg: AnyRef => {
        val (body, properties) = serialize(msg, serializer)
        val publish = Publish(exchange, routingKey, body, Some(properties), mandatory = mandatory, immediate = immediate)
        val future = (client ? RpcClient.Request(publish :: Nil, 1))(timeout).mapTo[RpcClient.Response]
        val dest = sender
        future.onComplete {
          case Right(result) => {
            val delivery = result.deliveries(0)
            val response = deserialize(delivery.body, delivery.properties)
            response match {
              case Failure(error, reason) => dest ! akka.actor.Status.Failure(new RuntimeException("error:%d reason:%s" format(error, reason)))
              case other => dest ! other
            }
          }
          case Left(error) => dest ! akka.actor.Status.Failure(error)
        }
      }
    }
  }

  /**
   * standard  one-request/one response proxy, which allows to write (myActor ? MyRequest).mapTo[MyResponse]
   * @param client AMQP RPC Client
   * @param exchange exchange to which requests will be sent
   * @param routingKey routing key with which requests will be sent
   * @param serializer message serializer
   * @param mandatory AMQP mandatory flag used to sent requests with; default to true
   * @param immediate AMQP immediate flag used to sent requests with; default to false; use with caution !!
   * @param deliveryMode AMQP delivery mode to sent request with; defaults to 1 (
   */
  class ProxySender(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer, mandatory: Boolean = true, immediate: Boolean = false, deliveryMode: Int = 1) extends Actor {

    protected def receive = {
      case msg: AnyRef => {
        val (body, props) = serialize(msg, serializer)
        val propsWithDeliveryMode = new BasicProperties.Builder().contentEncoding(props.getContentEncoding).contentType(props.getContentType).deliveryMode(deliveryMode).build
        val publish = Publish(exchange, routingKey, body, Some(propsWithDeliveryMode), mandatory = mandatory, immediate = immediate)
        client ! RpcClient.Request(publish :: Nil, 0) // 0 means no answer is expected
      }
    }
  }
}
