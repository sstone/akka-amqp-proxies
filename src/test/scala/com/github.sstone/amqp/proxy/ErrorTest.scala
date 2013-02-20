package com.github.sstone.amqp.proxy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.testkit.TestKit
import akka.actor.{Actor, Props, ActorSystem}
import akka.pattern.ask
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import concurrent.{Future, Await, ExecutionContext}
import concurrent.duration._
import com.rabbitmq.client.ConnectionFactory
import com.github.sstone.amqp.{Amqp, RpcClient, RpcServer, ConnectionOwner}
import com.github.sstone.amqp.Amqp.{ChannelParameters, QueueParameters, ExchangeParameters}
import serializers.JsonSerializer
import util.{Success, Failure}

@RunWith(classOf[JUnitRunner])
class ErrorTest extends TestKit(ActorSystem("TestSystem")) with WordSpec with ShouldMatchers {
  "AMQP Proxy" should {
    "handle errors in" in {
      import ExecutionContext.Implicits.global
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = QueueParameters(name = "error", passive = false, autodelete = true)
      val channelParams = Some(ChannelParameters(qos = 1))

      case class ErrorRequest(foo: String)

      val nogood = system.actorOf(Props(new Actor() {
        def receive = {
          case ErrorRequest(foo) => sender ! akka.actor.Status.Failure(new RuntimeException("crash"))
        }
      }))
      // create an AMQP proxy server which consumes messages from the "calculator" queue and passes
      // them to our Calculator actor
      val server = ConnectionOwner.createActor(
        conn,
        Props(new RpcServer(queue, exchange, "error", new AmqpProxy.ProxyServer(nogood), channelParams)),
        2 second)

      // create an AMQP proxy client in front of the "calculator queue"
      val client = ConnectionOwner.createActor(conn, Props(new RpcClient()), 5 second)
      val proxy = system.actorOf(
        Props(new AmqpProxy.ProxyClient(client, "amq.direct", "error", JsonSerializer)),
        name = "proxy")

      Amqp.waitForConnection(system, server).await()
      implicit val timeout: akka.util.Timeout = 5 seconds

      val thrown = intercept[AmqpProxyException] {
        Await.result(proxy ? ErrorRequest("test"), 5 seconds )
      }
      assert(thrown.getMessage === "crash")
    }
  }
}