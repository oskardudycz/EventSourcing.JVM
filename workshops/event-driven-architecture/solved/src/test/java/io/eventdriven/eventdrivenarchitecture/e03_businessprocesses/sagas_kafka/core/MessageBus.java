package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IMessageBus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class MessageBus implements IMessageBus, IEventBus, ICommandBus {
  private final Map<String, List<Consumer<Object>>> eventHandlers = new LinkedHashMap<>();
  private final Map<String, Consumer<Object>> commandHandlers = new LinkedHashMap<>();
  private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  @Value("${app.kafka.topic:hotel-financial}")
  private String topicName;

  public void publish(Object[] events) {
    for (Object event : events) {
      // Apply middleware ONLY to outgoing messages for test capture
      for (var middleware : middlewares)
        middleware.accept(event);

      // Send to Kafka using our proven serializer
      var producerRecord = KafkaMessageSerializer.serialize(topicName, event);
      kafkaTemplate.send(producerRecord);
    }
  }

  public <Event> IEventBus subscribe(Class<Event> eventClass, Consumer<Event> handler) {
    eventHandlers.compute(eventClass.getTypeName(), (eventType, consumers) -> {
      if (consumers == null)
        consumers = new CopyOnWriteArrayList<>();

      consumers.add(
        event -> handler.accept(eventClass.cast(event))
      );

      return consumers;
    });

    return this;
  }

  public void send(Object[] commands) {
    for (Object command : commands) {
      for (var middleware : middlewares)
        middleware.accept(command);

      var producerRecord = KafkaMessageSerializer.serialize(topicName, command);
      kafkaTemplate.send(producerRecord);
    }
  }

  public <Command> ICommandBus handle(Class<Command> commandClass, Consumer<Command> handler) {
    commandHandlers.compute(commandClass.getTypeName(), (commandType, consumer) ->
      message -> handler.accept(commandClass.cast(message))); // Always use new handler (allows re-registration)

    return this;
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }

  public void clearMiddleware() {
    middlewares.clear();
  }

  @KafkaListener(topics = "${app.kafka.topic:hotel-financial}", groupId = "hotel-financial-service")
  public void handleMessage(ConsumerRecord<String, String> record) {
    try {
      var messageOptional = KafkaMessageSerializer.deserialize(record);
      if (messageOptional.isEmpty()) {
        System.err.println("ERROR: Failed to deserialize Kafka message");
        return;
      }

      Object message = messageOptional.get();

      // Route to event handlers (for events)
      var eventHandlerList = eventHandlers.get(message.getClass().getTypeName());
      if (eventHandlerList != null) {
        for (var handle : eventHandlerList) {
          handle.accept(message);
        }
      }

      // Route to command handlers (for commands)
      var commandHandler = commandHandlers.get(message.getClass().getTypeName());
      if (commandHandler != null) {
        commandHandler.accept(message);
      }
    } catch (Exception e) {
      System.err.println("ERROR: Failed to handle Kafka message: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
