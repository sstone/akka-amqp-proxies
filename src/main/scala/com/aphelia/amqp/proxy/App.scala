package com.aphelia.amqp.proxy

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import calculator.Calculator.{AddResponse, AddRequest}
import scala.concurrent.duration._
import com.rabbitmq.client.ConnectionFactory
import akka.routing.SmallestMailboxRouter
import com.aphelia.amqp.{Amqp, RpcClient, RpcServer, ConnectionOwner}
import com.aphelia.amqp.Amqp.{ChannelParameters, QueueParameters, ExchangeParameters}
import serializers.ProtobufSerializer
import util.{Failure, Success}
import concurrent.ExecutionContext
import akka.serialization.{SerializationExtension, JavaSerializer}
import com.aphelia.amqp.proxy.Calculator


class Calculator extends Actor {
  def receive = {
    case request: AddRequest => sender ! AddResponse.newBuilder().setSum(request.getX + request.getY).build()
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
    import ExecutionContext.Implicits.global
    implicit val timeout: Timeout = 5 second

    for (x <- 0 to 5) {
      for (y <- 0 to 5) {
        (calc ? AddRequest.newBuilder().setX(x).setY(y).build()).onComplete {
          case Success(response: AddResponse) => println("%d + %d = %d".format(x, y, response.getSum))
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
    val client = ConnectionOwner.createActor(conn, Props(new RpcClient()), 5 second)
    Amqp.waitForConnection(system, client).await()
    val proxy = system.actorOf(
      Props(new AmqpProxy.ProxyClient(client, "amq.direct", "calculator", ProtobufSerializer)),
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