package com.github.sstone.amqp.proxy

import akka.actor.{Props, ActorLogging, Actor, ActorRef}
import akka.serialization.Serializer
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import concurrent.{ExecutionContext, Future, Await}
import concurrent.duration._
import util.{Try, Failure, Success}
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.AMQP
import org.slf4j.LoggerFactory
import serializers.JsonSerializer
import com.github.sstone.amqp.{Amqp, RpcClient, RpcServer}
import com.github.sstone.amqp.RpcServer.ProcessResult
import com.github.sstone.amqp.Amqp.{Publish, Delivery}

/**
 * Thrown when an error occurred on the "server" side and was sent back to the client
 * If you have a server Actor and create an AMQP proxy for it, then:
 * {{{
 *    proxy ? message
 * }}}
 * will behave as if you had written;
 * {{{
 *    server ? message
 * }}}
 * and server had sent back an `akka.actor.Status.ServerFailure(new AmqpProxyException(message)))`
 * @param message error message
 */
class AmqpProxyException(message: String, throwableAsString: String) extends RuntimeException(message)

object AmqpProxy {

  /**
   * "server" side failure, that will be serialized and sent back to the client proxy
   * @param message error message
   */
  private case class ServerFailure(message: String, throwableAsString: String)

  /**
   * serialize a message and return a (blob, AMQP properties) tuple. The following convention is used for the AMQP properties
   * the message will be sent with:
   * <ul>
   * <li>contentEncoding is set to the name of the serializer that is used</li>
   * <li>contentType is set to the name of the message class</li>
   * </ul>
   * @param msg input message
   * @param serializer serializer
   * @param deliveryMode AMQP delivery mode that will be included in the returned AMQP properties
   * @return a (blob, properties) tuple where blob is the serialized message and properties the AMQP properties the message
   *         should be sent with.
   */
  def serialize(msg: AnyRef, serializer: Serializer, deliveryMode: Int = 1) = {
    val body = serializer.toBinary(msg)
    val props = new BasicProperties.Builder().contentEncoding(Serializers.serializerToName(serializer)).contentType(msg.getClass.getName).deliveryMode(deliveryMode).build
    (body, props)
  }

  /**
   * deserialize a message
   * @param body serialized message
   * @param props AMQP properties, which contain meta-data for the serialized message
   * @return a (deserialized message, serializer) tuple
   * @see [[com.github.sstone.amqp.proxy.AmqpProxy.serialize( )]]
   */
  def deserialize(body: Array[Byte], props: AMQP.BasicProperties) = {
    require(props.getContentType != null && props.getContentType != "", "content type is not specified")
    val serializer = props.getContentEncoding match {
      case "" | null => JsonSerializer // use JSON if not serialization format was specified
      case encoding => Serializers.nameToSerializer(encoding)
    }
    (serializer.fromBinary(body, Some(Class.forName(props.getContentType))), serializer)
  }

  class ProxyServer(server: ActorRef, timeout: Timeout = 30 seconds) extends RpcServer.IProcessor {

    import ExecutionContext.Implicits.global

    lazy val logger = LoggerFactory.getLogger(classOf[ProxyServer])

    def process(delivery: Delivery) = {
      logger.trace("consumer %s received %s with properties %s".format(delivery.consumerTag, delivery.envelope, delivery.properties))

      Try(deserialize(delivery.body, delivery.properties)) match {
        case Success((request, serializer)) => {
          logger.debug("handling delivery of type %s".format(request.getClass.getName))

          val future = for {
            response <- (server ? request)(timeout).mapTo[AnyRef]
            _ = logger.debug("sending response of type %s".format(response.getClass.getName))
            (body, props) = serialize(response, serializer)
          } yield ProcessResult(Some(body), Some(props))

          future.onFailure {
            case cause => logger.error(s"inner call to server actor $server failed", cause)
          }
          future
        }
        case Failure(cause) => {
          logger.error("deserialization failed", cause)
          Future.failed(cause)
        }
      }
    }

    def onFailure(delivery: Delivery, e: Throwable) = {
      val (body, props) = serialize(ServerFailure(e.getMessage, e.toString), JsonSerializer)
      ProcessResult(Some(body), Some(props))
    }
  }

  object ProxyClient {
    def props(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer, timeout: Timeout = 30 seconds, mandatory: Boolean = true, immediate: Boolean = false, deliveryMode: Int = 1): Props =
      Props(new ProxyClient(client, exchange, routingKey, serializer, timeout, mandatory, immediate, deliveryMode))
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

    import ExecutionContext.Implicits.global

    def receive = {
      case msg: AnyRef => {
        Try(serialize(msg, serializer, deliveryMode = deliveryMode)) match {
          case Success((body, props)) => {
            // publish the serialized message (and tell the RPC client that we expect one response)
            val publish = Publish(exchange, routingKey, body, Some(props), mandatory = mandatory, immediate = immediate)
            val future = (client ? RpcClient.Request(publish :: Nil, 1))(timeout).mapTo[RpcClient.Response].map(result => {
              val delivery = result.deliveries(0)
              val (response, serializer) = deserialize(delivery.body, delivery.properties)
              response match {
                case ServerFailure(message, throwableAsString) => akka.actor.Status.Failure(new AmqpProxyException(message, throwableAsString))
                case _ => response
              }
            })
            future.pipeTo(sender)
          }
          case Failure(cause) => sender ! akka.actor.Status.Failure(new AmqpProxyException("Serialization error", cause.getMessage))
        }
      }
    }
  }

  /**
   * "fire-and-forget" proxy, which allows to write myActor ! MyRequest
   * @param client AMQP RPC Client
   * @param exchange exchange to which requests will be sent
   * @param routingKey routing key with which requests will be sent
   * @param serializer message serializer
   * @param mandatory AMQP mandatory flag used to sent requests with; default to true
   * @param immediate AMQP immediate flag used to sent requests with; default to false; use with caution !!
   * @param deliveryMode AMQP delivery mode to sent request with; defaults to 1
   */
  class ProxySender(client: ActorRef, exchange: String, routingKey: String, serializer: Serializer, mandatory: Boolean = true, immediate: Boolean = false, deliveryMode: Int = 1) extends Actor with ActorLogging {

    def receive = {
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
