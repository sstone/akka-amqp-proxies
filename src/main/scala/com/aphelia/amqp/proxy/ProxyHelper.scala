package com.aphelia.amqp.proxy

import com.aphelia.amqp.{RpcServer, RabbitMQConnection}
import akka.actor.{ActorRef, Props}
import com.aphelia.amqp.Amqp.{ExchangeParameters, QueueParameters, ChannelParameters, Binding}
import com.aphelia.amqp.proxy.AmqpProxy.{ProxyForwarder, ProxyServer}

trait ProxyHelper extends RabbitMQConnection {

  def createProxyRpcServer(bindings: List[Binding], channelParams: Option[ChannelParameters], handler: ActorRef) = {
    createChild(Props(new RpcServer(bindings, new ProxyServer(handler), channelParams)), None)
  }

  def createProxyRpcServer(queue: QueueParameters, exchange: ExchangeParameters, routingKey: String, channelParams: Option[ChannelParameters], handler: ActorRef) = {
    createChild(Props(new RpcServer(List(Binding(exchange, queue, routingKey, false)), new ProxyServer(handler), channelParams)), None)
  }

  def createProxyRpcServer(queue: QueueParameters, routingKey: String, handler: ActorRef, channelParams: Option[ChannelParameters] = Some(ChannelParameters(qos = 1))) = {
    createChild(Props(new RpcServer(List(Binding(ExchangeParameters("amq.direct", true, "direct", true, false), queue, routingKey, false)), new ProxyServer(handler), channelParams)), None)
  }

  def createProxyForwarder(bindings: List[Binding], channelParams: Option[ChannelParameters], handler: ActorRef) = {
    createChild(Props(new RpcServer(bindings, new ProxyForwarder(handler), channelParams)), None)
  }

  def createProxyForwarder(queue: QueueParameters, exchange: ExchangeParameters, routingKey: String, channelParams: Option[ChannelParameters], handler: ActorRef) = {
    createChild(Props(new RpcServer(List(Binding(exchange, queue, routingKey, false)), new ProxyForwarder(handler), channelParams)), None)
  }

  def createProxyForwarder(queue: QueueParameters, routingKey: String, handler: ActorRef, channelParams: Option[ChannelParameters] = Some(ChannelParameters(qos = 1))) = {
    createChild(Props(new RpcServer(List(Binding(ExchangeParameters("amq.direct", true, "direct", true, false), queue, routingKey, false)), new ProxyForwarder(handler), channelParams)), None)
  }

}
