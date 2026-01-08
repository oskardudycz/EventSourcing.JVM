package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ITestableMessageBus;
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
public class MessageBus implements ITestableMessageBus, IEventBus, ICommandBus {
  private final Map<String, List<Consumer<Object>>> messageHandlers = new LinkedHashMap<>();
  private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
  private final Tracer tracer;
  private final TransformingMessageDeserializer deserializer;

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Value("${app.kafka.topic:hotel-financial}")
  private String topicName;

  public MessageBus(Tracer tracer, TransformingMessageDeserializer deserializer) {
    this.tracer = tracer;
    this.deserializer = deserializer;
  }

  public <Command> void send(List<Command> commands) {
    for (Object command : commands) {
      for (var middleware : middlewares)
        middleware.accept(command);

      var span = tracer.nextSpan().name("messagebus.send").start();
      try (var _ = tracer.withSpan(span)) {
        var record = new ProducerRecord<String, Object>(topicName, command);
        setHeadersFromBaggage(record, tracer);
        kafkaTemplate.send(record).get();
      } catch (Exception e) {
        span.error(e);
        throw new RuntimeException("Failed to send message", e);
      } finally {
        span.end();
      }
    }
  }

  public <Event> void publish(List<Event> events) {
    for (Object event : events) {
      for (var middleware : middlewares)
        middleware.accept(event);

      var span = tracer.nextSpan().name("messagebus.publish").start();
      try (var _ = tracer.withSpan(span)) {
        var record = new ProducerRecord<String, Object>(topicName, event);
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
    var messageOptional = deserializer.deserialize(record);
    if (messageOptional.isEmpty()) {
      System.err.println("ERROR: Failed to deserialize Kafka message");
      return;
    }

    Object message = messageOptional.get();

    var handlers = messageHandlers.get(message.getClass().getTypeName());

    if (handlers == null || handlers.isEmpty()) {
      System.out.println("WARNING: No handler found for message " + message);
      return;
    }

    for (var handle : handlers) {
      var span = tracer.nextSpan().name("messagebus.handle").start();
      try (var _ = tracer.withSpan(span)) {
        handle.accept(message);
      } catch (Exception ex) {
        System.err.println("ERROR: Failed to handle Kafka message: " + ex.getMessage());
        ex.printStackTrace();
      }
    }
  }

  public <Event> IEventBus subscribe(Class<Event> eventClass, Consumer<Event> handler) {
    messageHandlers.compute(eventClass.getTypeName(), (eventType, consumers) -> {
      if (consumers == null)
        consumers = new CopyOnWriteArrayList<>();

      consumers.add(
        event -> handler.accept(eventClass.cast(event))
      );

      return consumers;
    });

    return this;
  }

  public <Command> ICommandBus handle(Class<Command> commandClass, Consumer<Command> handler) {
    messageHandlers.compute(commandClass.getTypeName(), (commandType, consumers) -> {
        if (consumers == null)
          consumers = new CopyOnWriteArrayList<>();
        else
          // we allow only a single command handler
          consumers.clear();

        consumers.add(
          command -> handler.accept(commandClass.cast(command))
        );

        return consumers;
      }
    );

    return this;
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }

  public void clearMiddleware() {
    middlewares.clear();
  }
}
