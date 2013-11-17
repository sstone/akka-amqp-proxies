package com.github.sstone.amqp.proxy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.TestKit
import akka.actor.{Actor, Props, ActorSystem}
import akka.pattern.ask
import concurrent.Await
import concurrent.duration._
import com.rabbitmq.client.ConnectionFactory
import com.github.sstone.amqp.{Amqp, RpcClient, RpcServer, ConnectionOwner}
import com.github.sstone.amqp.Amqp.{ChannelParameters, QueueParameters, ExchangeParameters}
import serializers.JsonSerializer

@RunWith(classOf[JUnitRunner])
class ErrorTest extends TestKit(ActorSystem("TestSystem")) with WordSpec with ShouldMatchers {
  implicit val timeout: akka.util.Timeout = 5 seconds

  "AMQP Proxy" should {

    "handle server errors in" in {
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = QueueParameters(name = "error", passive = false, autodelete = true)
      val channelParams = ChannelParameters(qos = 1)

      case class ErrorRequest(foo: String)

      val nogood = system.actorOf(Props(new Actor() {
        def receive = {
          case ErrorRequest(foo) => sender ! akka.actor.Status.Failure(new RuntimeException("crash"))
        }
      }))
      // create an AMQP proxy server which consumes messages from the "error" queue and passes
      // them to our nogood actor
      val server = ConnectionOwner.createChildActor(
        conn,
        RpcServer.props(queue, exchange, "error", new AmqpProxy.ProxyServer(nogood), channelParams))

      // create an AMQP proxy client in front of the "error queue"
      val client = ConnectionOwner.createChildActor(conn, RpcClient.props())
      val proxy = system.actorOf(
        AmqpProxy.ProxyClient.props(client, "amq.direct", "error", JsonSerializer),
        name = "proxy")

      Amqp.waitForConnection(system, server).await()

      val thrown = evaluating(Await.result(proxy ? ErrorRequest("test"), 5 seconds)) should produce[AmqpProxyException]
      thrown.getMessage should be("crash")
    }

    "handle client-side serialization errors" in {
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)))
      val client = ConnectionOwner.createChildActor(conn, RpcClient.props())
      val proxy = system.actorOf(AmqpProxy.ProxyClient.props(client, "amq.direct", "client_side_error", JsonSerializer))

      val badrequest = Map(1 -> 1) // lift-json will not serialize this, Map keys must be Strings
      val thrown = evaluating(Await.result(proxy ? badrequest, 5 seconds)) should produce[AmqpProxyException]
      thrown.getMessage should include("Serialization")
    }
  }
}