package com.github.sstone.amqp.proxy

import akka.actor.{Actor, Props, ActorSystem}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import calculator.Calculator.{AddResponse, AddRequest}
import com.github.sstone.amqp.Amqp.Binding
import com.github.sstone.amqp.Amqp.ChannelParameters
import com.github.sstone.amqp.Amqp.ExchangeParameters
import com.github.sstone.amqp.Amqp.QueueParameters
import com.github.sstone.amqp.Amqp._
import com.github.sstone.amqp.{Amqp, RpcClient, RpcServer, ConnectionOwner}
import com.rabbitmq.client.ConnectionFactory
import concurrent.duration._
import concurrent.{Future, Await, ExecutionContext}
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import serializers.ProtobufSerializer

@RunWith(classOf[JUnitRunner])
class RemoteGpbCallTest extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpec with ShouldMatchers {
  "AMQP Proxy" should {
    "handle GPB calls" in {
      import ExecutionContext.Implicits.global
      val connFactory = new ConnectionFactory()
      val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
      val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
      val queue = QueueParameters(name = "calculator-gpb", passive = false, autodelete = true)

      // create a simple calculator actor
      val calc = system.actorOf(Props(new Actor() {
        def receive = {
          case request: AddRequest => sender ! AddResponse.newBuilder().setX(request.getX).setY(request.getY).setSum(request.getX + request.getY).build()
        }
      }))
      // create an AMQP proxy server which consumes messages from the "calculator" queue and passes
      // them to our Calculator actor
      val server = ConnectionOwner.createChildActor(conn, RpcServer.props(new AmqpProxy.ProxyServer(calc), channelParams = Some(ChannelParameters(qos = 1))))
      Amqp.waitForConnection(system, server).await(5, TimeUnit.SECONDS)

      server ! AddBinding(Binding(exchange, queue, "calculator-gpb"))
      expectMsgPF() {
        case Amqp.Ok(AddBinding(_), _) => true
      }
      // create an AMQP proxy client in front of the "calculator queue"
      val client = ConnectionOwner.createChildActor(conn, RpcClient.props())
      val proxy = system.actorOf(
        AmqpProxy.ProxyClient.props(client, "amq.direct", "calculator-gpb", ProtobufSerializer),
        name = "proxy")

      Amqp.waitForConnection(system, client).await(5, TimeUnit.SECONDS)
      implicit val timeout: akka.util.Timeout = 5 seconds

      val futures = for (x <- 0 until 5; y <- 0 until 5) yield (proxy ? AddRequest.newBuilder.setX(x).setY(y).build()).mapTo[AddResponse]
      val result = Await.result(Future.sequence(futures), 5 seconds)
      assert(result.length === 25)
      assert(result.filter(r => r.getSum != r.getX + r.getY).isEmpty)
    }
  }
}