import pika
import uuid
import json

class CalculatorRpcClient(object):
    def __init__(self):
        self.connection = pika.BlockingConnection(pika.ConnectionParameters(host='localhost'))

        self.channel = self.connection.channel()

        result = self.channel.queue_declare(exclusive=True)
        self.callback_queue = result.method.queue

        self.channel.basic_consume(self.on_response, no_ack=True, queue=self.callback_queue)

    def on_response(self, ch, method, props, body):
        if self.corr_id == props.correlation_id:
            self.response = body

    def add(self, x, y):
        self.response = None
        self.corr_id = str(uuid.uuid4())
        msg = json.dumps({ "x":x, "y":y })
        self.channel.basic_publish(exchange='',
                                   routing_key='calculator',
                                   properties=pika.BasicProperties(
                                         reply_to = self.callback_queue,
                                         correlation_id = self.corr_id,
										 content_type = 'com.github.sstone.amqp.proxy.Demo$AddRequest',
										 content_encoding = 'json'
                                         ),
                                   body=msg)
        while self.response is None:
            self.connection.process_data_events()
        raw = self.response
        decoded = json.loads(raw)
        return decoded['sum']

calculator_rpc = CalculatorRpcClient()

x = 1
y = 2
print " [x] Requesting ", x, " + ", y
response = calculator_rpc.add(x, y)
print " [.] Got %r" % (response,)