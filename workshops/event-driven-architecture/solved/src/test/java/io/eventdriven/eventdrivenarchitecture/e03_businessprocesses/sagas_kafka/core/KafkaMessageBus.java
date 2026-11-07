package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.micrometer.tracing.Tracer;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.KafkaMessageBaggageHandler.setHeadersFromBaggage;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class KafkaMessageBus implements MessageBus {
  private final Map<String, List<Consumer<Object>>> messageHandlers = new LinkedHashMap<>();
  private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
  private final Tracer tracer;
  private final TransformingMessageDeserializer deserializer;

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Value("${app.kafka.topic:hotel-financial}")
  private String topicName;

  public KafkaMessageBus(Tracer tracer, TransformingMessageDeserializer deserializer) {
    this.tracer = tracer;
    this.deserializer = deserializer;
  }

  public <Message> void publish(List<Message> messages) {
    for (Object message : messages) {
      for (var middleware : middlewares)
        middleware.accept(message);

      var span = tracer.nextSpan().name("messagebus.publish").start();
      try (var _ = tracer.withSpan(span)) {
        var record = new ProducerRecord<String, Object>(topicName, message);
        setHeadersFromBaggage(record, tracer);
        kafkaTemplate.send(record).get();
      } catch (Exception e) {
        span.error(e);
        // Note: Sometimes you can't just ignore, you might need to inject some strategy based on error, like:
        // - retries,
        // - dead letter,
        // - stopping processing.
        throw new RuntimeException("Failed to send message", e);
      } finally {
        span.end();
      }
    }
  }

  @KafkaListener(topics = "${app.kafka.topic:hotel-financial}", groupId = "${app.kafka.groupId:hotel-financial-service}")
  public void handleMessage(ConsumerRecord<String, String> record) {
    Object message = deserializer.deserialize(record);

    var handlers = messageHandlers.get(message.getClass().getTypeName());

    if (handlers == null || handlers.isEmpty()) {
      System.out.println("WARNING: No handler found for message " + message);
      return;
    }

    for (var handle : handlers) {
      var span = tracer.nextSpan().name("messagebus.handle").start();
      try (var _ = tracer.withSpan(span)) {
        handle.accept(message);
      } finally {
        span.end();
      }
    }
  }

  public <Message> MessageBus subscribe(Class<Message> messageClass, Consumer<Message> handler) {
    messageHandlers.compute(messageClass.getTypeName(), (messageType, consumers) -> {
      if (consumers == null)
        consumers = new CopyOnWriteArrayList<>();

      consumers.add(
        message -> handler.accept(messageClass.cast(message))
      );

      return consumers;
    });

    return this;
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }

  public void clearMiddleware() {
    middlewares.clear();
  }
}
