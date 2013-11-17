package com.github.sstone.amqp.proxy

import akka.testkit.TestKit
import akka.actor.{Actor, Props, ActorSystem}
import akka.pattern.ask
import calculator.Calculator.{AddResponse, AddRequest}
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import concurrent.{Future, Await, ExecutionContext}
import com.rabbitmq.client.ConnectionFactory
import com.github.sstone.amqp.{Amqp, RpcClient, RpcServer, ConnectionOwner}
import com.github.sstone.amqp.Amqp.{ChannelParameters, QueueParameters, ExchangeParameters}
import serializers.{ProtobufSerializer, JsonSerializer}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import concurrent.duration._

@RunWith(classOf[JUnitRunner])
class RemoteGpbCallTest extends TestKit(ActorSystem("TestSystem")) with WordSpec with ShouldMatchers {
  "AMQP Proxy" should {
    "handle GPB calls" in {
      import ExecutionContext.Implicits.global
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = QueueParameters(name = "calculator-gpb", passive = false, autodelete = true)
      val channelParams = ChannelParameters(qos = 1)

      // create a simple calculator actor
      val calc = system.actorOf(Props(new Actor() {
        def receive = {
          case request: AddRequest => sender ! AddResponse.newBuilder().setX(request.getX).setY(request.getY).setSum(request.getX + request.getY).build()
        }
      }))
      // create an AMQP proxy server which consumes messages from the "calculator" queue and passes
      // them to our Calculator actor
      val server = ConnectionOwner.createChildActor(
        conn,
        RpcServer.props(queue, exchange, "calculator-gpb", new AmqpProxy.ProxyServer(calc), channelParams))

      // create an AMQP proxy client in front of the "calculator queue"
      val client = ConnectionOwner.createChildActor(conn, RpcClient.props())
      val proxy = system.actorOf(
        AmqpProxy.ProxyClient.props(client, "amq.direct", "calculator-gpb", ProtobufSerializer),
        name = "proxy")

      Amqp.waitForConnection(system, server).await()
      implicit val timeout: akka.util.Timeout = 5 seconds

      val futures = for (x <- 0 to 5; y <- 0 to 5) yield (proxy ? AddRequest.newBuilder.setX(x).setY(y).build()).mapTo[AddResponse]
      val result = Await.result(Future.sequence(futures), 5 seconds)
      assert(result.filter(r => r.getSum != r.getX + r.getY).isEmpty)
    }
  }
}