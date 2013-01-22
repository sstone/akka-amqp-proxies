package com.github.sstone.amqp.proxy

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.serialization.Serializer
import com.github.sstone.amqp.{Amqp, RpcClient, RpcServer}
import com.github.sstone.amqp.RpcServer.ProcessResult
import com.rabbitmq.client.AMQP.BasicProperties
import com.github.sstone.amqp.Amqp.{Publish, Delivery}
import akka.dispatch.{Future, Await}
import akka.util.Timeout
import akka.pattern.ask
import akka.util.duration._
import grizzled.slf4j.Logging
import serializers.JsonSerializer
import com.rabbitmq.client.AMQP


object AmqpProxy {

  case class Failure(error: Int, reason: String)

  def serialize(msg: AnyRef, serializer: Serializer, deliveryMode: Int = 1) = {
    val body = serializer.toBinary(msg)
    val props = new BasicProperties.Builder().contentEncoding(Serializers.serializerToName(serializer)).contentType(msg.getClass.getName).deliveryMode(deliveryMode).build
    (body, props)
  }

  def deserialize(body: Array[Byte], props: AMQP.BasicProperties) = {
    Serializers.nameToSerializer(props.getContentEncoding).fromBinary(body, Some(Class.forName(props.getContentType)))
  }

  class ProxyServer(server: ActorRef, timeout: Timeout = 30 seconds) extends RpcServer.IProcessor with Logging {

    def process(delivery: Delivery) = {
      trace("consumer %s received %s with properties %s".format(delivery.consumerTag, delivery.envelope, delivery.properties))
      val request = deserialize(delivery.body, delivery.properties)
      debug("handling delivery of type %s".format(request.getClass.getName))
      (server ? request)(timeout).mapTo[AnyRef].map {
        response => {
          debug("sending response of type %s".format(response.getClass.getName))
          val (body, props) = serialize(response, Serializers.nameToSerializer(delivery.properties.getContentEncoding))
          ProcessResult(Some(body), Some(props)) // we answer with the same encoding type
        }
      }
    }

    def onFailure(delivery: Delivery, e: Throwable) = {
      val (body, props) = serialize(Failure(1, e.toString), JsonSerializer)
      ProcessResult(Some(body), Some(props))
    }
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
        val (body, props) = serialize(msg, serializer, deliveryMode = deliveryMode)
        val publish = Publish(exchange, routingKey, body, Some(props), mandatory = mandatory, immediate = immediate)
        val future: Future[RpcClient.Response] = (client ? RpcClient.Request(publish :: Nil, 1))(timeout).mapTo[RpcClient.Response]
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
  class ProxySender(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer, mandatory: Boolean = true, immediate: Boolean = false, deliveryMode: Int = 1) extends Actor with ActorLogging {

    protected def receive = {
      case Amqp.Ok(request, _) => log.debug("successfully processed request %s".format(request))
      case Amqp.Error(request, error) => log.error("error while processing %s : %s".format(request, error))
      case msg: AnyRef => {
        val (body, props) = serialize(msg, serializer, deliveryMode = deliveryMode)
        val publish = Publish(exchange, routingKey, body, Some(props), mandatory = mandatory, immediate = immediate)
        log.debug("sending %s to %s".format(publish, client))
        client ! publish
      }
    }
  }

}
