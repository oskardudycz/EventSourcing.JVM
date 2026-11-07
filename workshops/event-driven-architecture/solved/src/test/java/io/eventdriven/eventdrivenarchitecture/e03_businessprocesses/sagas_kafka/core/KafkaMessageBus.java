package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.micrometer.tracing.Tracer;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.KafkaMessageBaggageHandler.setHeadersFromBaggage;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Component
public class KafkaMessageBus implements MessageBus {
  private static final Logger log = LoggerFactory.getLogger(KafkaMessageBus.class);

  private final Map<String, List<Consumer<Object>>> messageHandlers = new ConcurrentHashMap<>();
  private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
  private final Tracer tracer;
  private final VersionedMessageConverter messageConverter;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final KafkaMessageBusProperties properties;

  public KafkaMessageBus(
    Tracer tracer,
    VersionedMessageConverter messageConverter,
    KafkaTemplate<String, Object> kafkaTemplate,
    KafkaMessageBusProperties properties
  ) {
    this.tracer = tracer;
    this.messageConverter = messageConverter;
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
  }

  public <Message> void publish(List<Message> messages) {
    for (Object message : messages) {
      for (var middleware : middlewares)
        middleware.accept(message);

      var record = new ProducerRecord<String, Object>(properties.topic(), message);
      setHeadersFromBaggage(record, tracer);
      try {
        kafkaTemplate.send(record).get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
      } catch (ExecutionException e) {
        // Note: Sometimes you can't just ignore, you might need to inject some strategy based on error, like:
        // - retries,
        // - dead letter,
        // - stopping processing.
        throw e.getCause() instanceof RuntimeException cause
          ? cause
          : new KafkaException("Failed to send message", e.getCause());
      } catch (TimeoutException e) {
        throw new KafkaException("No acknowledgement from Kafka within " + properties.publishTimeout(), e);
      } catch (InterruptedException e) {
        throw new InterruptException(e);
      }
    }
  }

  @KafkaListener(topics = "${app.kafka.topic}", groupId = "${app.kafka.group-id}")
  public void handleMessage(ConsumerRecord<String, byte[]> record) {
    var message = messageConverter.read(record);

    var handlers = messageHandlers.get(message.getClass().getTypeName());

    if (handlers == null || handlers.isEmpty()) {
      log.warn("No handler found for message {}", message);
      return;
    }

    for (var handle : handlers)
      handle.accept(message);
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
