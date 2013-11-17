package com.github.sstone.amqp.proxy

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import com.rabbitmq.client.ConnectionFactory
import akka.routing.SmallestMailboxRouter
import com.github.sstone.amqp.{Amqp, RpcClient, RpcServer, ConnectionOwner}
import com.github.sstone.amqp.Amqp.{ChannelParameters, QueueParameters, ExchangeParameters}
import serializers.JsonSerializer
import util.{Failure, Success}
import concurrent.ExecutionContext

object Demo {

  case class AddRequest(x: Int, y: Int)

  case class AddResponse(x: Int, y: Int, sum: Int)

  class Calculator extends Actor {
    def receive = {
      case AddRequest(x, y) => sender ! AddResponse(x, y, x + y)
    }
  }
}

import Demo._

object Server {
  def main(args: Array[String]) {
    val system = ActorSystem("MySystem")
    val calc = system.actorOf(Props[Calculator])
    val connFactory = new ConnectionFactory()
    connFactory.setHost("localhost")
    val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
    val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
    val queue = QueueParameters(name = "calculator", passive = false, autodelete = true)
    val channelParams = ChannelParameters(qos = 1)
    // create an AMQP RPC server which consumes messages from queue "calculator" and passes
    // them to our Calculator actor
    val server = ConnectionOwner.createChildActor(
      conn,
      RpcServer.props(queue, exchange, "calculator", new AmqpProxy.ProxyServer(calc), channelParams),
      name = Some("server"))

    Amqp.waitForConnection(system, server).await()
  }
}

object Client {
  def compute(calc: ActorRef) {
    import ExecutionContext.Implicits.global
    implicit val timeout: Timeout = 5 second

    for (x <- 0 to 5) {
      for (y <- 0 to 5) {
        (calc ? AddRequest(x, y)).onComplete {
          case Success(AddResponse(x1, y1, sum)) => println("%d + %d = %d".format(x1, y1, sum))
          case Failure(error) => println(error)
        }
      }
    }
  }

  def main(args: Array[String]) {
    val system = ActorSystem("MySystem")
    val connFactory = new ConnectionFactory()
    connFactory.setHost("localhost")
    // create a "connection owner" actor, which will try and reconnect automatically if the connection ins lost
    val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
    val client = ConnectionOwner.createChildActor(conn, RpcClient.props())
    Amqp.waitForConnection(system, client).await()
    val proxy = system.actorOf(
      AmqpProxy.ProxyClient.props(client, "amq.direct", "calculator", JsonSerializer),
      name = "proxy")
    Client.compute(proxy)
  }
}

object Local {
  def main(args: Array[String]) {
    val system = ActorSystem("MySystem")
    val calc = system.actorOf(Props[Calculator].withRouter(SmallestMailboxRouter(nrOfInstances = 8)))
    Client.compute(calc)
  }
}