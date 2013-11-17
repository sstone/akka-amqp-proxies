package com.github.sstone.amqp.proxy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.actor.{Props, Actor, ActorSystem}
import akka.testkit.TestKit
import akka.pattern.ask
import org.scalatest.{BeforeAndAfter, WordSpec}
import org.scalatest.matchers.ShouldMatchers
import com.rabbitmq.client.ConnectionFactory
import serializers.JsonSerializer
import concurrent.duration._
import com.github.sstone.amqp.{RpcClient, Amqp, RpcServer, ConnectionOwner}
import com.github.sstone.amqp.Amqp.{ChannelParameters, QueueParameters, ExchangeParameters}
import concurrent.{Await, Future, ExecutionContext}

@RunWith(classOf[JUnitRunner])
class RemoteJsonCallTest extends TestKit(ActorSystem("TestSystem")) with WordSpec with ShouldMatchers {
  "AMQP Proxy" should {
    "handle JSON calls" in {
      import ExecutionContext.Implicits.global
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = QueueParameters(name = "calculator", passive = false, autodelete = true)
      val channelParams = ChannelParameters(qos = 1)

      case class AddRequest(x: Int, y: Int)
      case class AddResponse(x: Int, y: Int, sum: Int)

      // create a simple calculator actor
      val calc = system.actorOf(Props(new Actor() {
        def receive = {
          case AddRequest(x, y) => sender ! AddResponse(x, y, x + y)
        }
      }))
      // create an AMQP proxy server which consumes messages from the "calculator" queue and passes
      // them to our Calculator actor
      val server = ConnectionOwner.createChildActor(
        conn,
        RpcServer.props(queue, exchange, "calculator", new AmqpProxy.ProxyServer(calc), channelParams))

      // create an AMQP proxy client in front of the "calculator queue"
      val client = ConnectionOwner.createChildActor(conn, RpcClient.props())
      val proxy = system.actorOf(
        AmqpProxy.ProxyClient.props(client, "amq.direct", "calculator", JsonSerializer),
        name = "proxy")

      Amqp.waitForConnection(system, server).await()
      implicit val timeout: akka.util.Timeout = 5 seconds

      val futures = for (x <- 0 to 5; y <- 0 to 5) yield (proxy ? AddRequest(x, y)).mapTo[AddResponse]
      val result = Await.result(Future.sequence(futures), 5 seconds)
      assert(result.filter(r => r.sum != r.x + r.y).isEmpty)
    }
  }
}
