require 'amqp'
require 'json'
require 'securerandom'

x = 1
y = 2

EventMachine.run do
  connection = AMQP.connect(:host => '127.0.0.1')
  puts "Connecting to AMQP broker. Running #{AMQP::VERSION} version of the gem..."

  channel = AMQP::Channel.new(connection)
  exchange = channel.default_exchange

  # create a JSON AddRequest message
  request = JSON.generate({:x => x, :y => y})

  # "standard" RPC pattern: create an exclusive private queue with a unique, randomized, broker-generated name
  # and publish message with the 'reply-to' property set to the name of this reply queue
  reply_queue = channel.queue("", :exclusive => true, :auto_delete => true) do |queue|
    exchange.publish request, {
        :routing_key => "calculator",
        :correlation_id => SecureRandom.uuid,
        :reply_to => queue.name,
        :content_encoding => "json",
        :content_type => "com.github.sstone.amqp.proxy.Demo$AddRequest"
    }
  end
  reply_queue.subscribe do |metadata, payload|
    puts "raw esponse for #{metadata.correlation_id}: #{metadata.content_encoding} #{metadata.content_type} #{payload.inspect}"

    # payload shoud look like { "x" : x, "y" : y, "sum" : sum }
    response = JSON.parse(payload)
    puts "#{x} + #{y} = #{response['sum']}"

    connection.close {
      EventMachine.stop { exit }
    }
  end

end