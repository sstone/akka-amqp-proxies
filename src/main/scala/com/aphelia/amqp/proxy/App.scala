package com.aphelia.amqp.proxy

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import com.rabbitmq.client.ConnectionFactory
import akka.routing.SmallestMailboxRouter
import com.aphelia.amqp.{Amqp, RpcClient, RpcServer, ConnectionOwner}
import com.aphelia.amqp.Amqp.{ChannelParameters, QueueParameters, ExchangeParameters}
import serializers.JsonSerializer

case class AddRequest(x: Int, y: Int)

case class AddResponse(sum: Int)

class Calculator extends Actor {
  protected def receive = {
    case AddRequest(a, b) => sender ! AddResponse(a + b)
  }
}

object Server {
  def main(args: Array[String]) {
    val system = ActorSystem("MySystem")
    val calc = system.actorOf(Props[Calculator])
    val connFactory = new ConnectionFactory()
    connFactory.setHost("localhost")
    val conn = system.actorOf(Props(new ConnectionOwner(connFactory)), name = "conn")
    val exchange = ExchangeParameters(name = "amq.direct", exchangeType = "", passive = true)
    val queue = QueueParameters(name = "calculator", passive = false, autodelete = true)
    val channelParams = Some(ChannelParameters(qos = 1))
    // create an AMQP RPC server which consumes messages from queue "calculator" and passes
    // them to our Calculator actor
    val server = ConnectionOwner.createActor(
      conn,
      Props(new RpcServer(queue, exchange, "calculator", new AmqpProxy.ProxyServer(calc), channelParams)),
      2 second)

    Amqp.waitForConnection(system, server).await()
  }
}

object Client {
  def compute(calc: ActorRef) {
    implicit val timeout: Timeout = 5 second

    for (x <- 0 to 5) {
      for (y <- 0 to 5) {
        (calc ? AddRequest(x, y)).onComplete {
          case Right(AddResponse(sum)) => println("%d + %d = %d".format(x, y, sum))
          case Left(error) => println(error)
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
    val client = ConnectionOwner.createActor(conn, Props(new RpcClient()), 5 second)
    Amqp.waitForConnection(system, client).await()
    val proxy = system.actorOf(
      Props(new AmqpProxy.ProxyClient(client, "amq.direct", "calculator", JsonSerializer)),
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