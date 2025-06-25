package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ITestableMessageBus;
import io.micrometer.tracing.Tracer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.RabbitMessageBaggageHandler.setHeadersFromBaggage;

@Component
public class MessageBus implements ITestableMessageBus, IEventBus, ICommandBus {
  private final Map<String, List<Consumer<Object>>> messageHandlers = new LinkedHashMap<>();
  private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
  private final Tracer tracer;
  private final TransformingMessageDeserializer deserializer;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.exchange:hotel-financial}")
  private String exchangeName;

  public MessageBus(Tracer tracer, TransformingMessageDeserializer deserializer) {
    this.tracer = tracer;
    this.deserializer = deserializer;
  }

  public void send(Object[] commands) {
    for (Object command : commands) {
      for (var middleware : middlewares)
        middleware.accept(command);

      var routingKey = buildRoutingKey(command);
      var span = tracer.nextSpan().name("messagebus.send").start();
      try (var _ = tracer.withSpan(span)) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, command, message -> {
          setHeadersFromBaggage(message, tracer);
          return message;
        });
      } catch (Exception e) {
        span.error(e);
        throw new RuntimeException("Failed to send message", e);
      } finally {
        span.end();
      }
    }
  }

  public void publish(Object[] events) {
    for (Object event : events) {
      for (var middleware : middlewares)
        middleware.accept(event);

      var routingKey = buildRoutingKey(event);

      var span = tracer.nextSpan().name("messagebus.publish").start();
      try (var _ = tracer.withSpan(span)) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, event, message -> {
          setHeadersFromBaggage(message, tracer);
          return message;
        });
      } catch (Exception e) {
        span.error(e);
        throw new RuntimeException("Failed to send message", e);
      } finally {
        span.end();
      }
    }
  }

  @RabbitListener(
    bindings = @QueueBinding(
      value = @Queue(value = "${app.rabbitmq.exchange:hotel-financial}-saga", durable = "true"),
      exchange = @Exchange(value = "${app.rabbitmq.exchange:hotel-financial}", type = ExchangeTypes.TOPIC),
      key = "#"
    ),
    containerFactory = "rabbitListenerContainerFactory"
  )
  public void handleMessage(Message rawMessage) {
    try {
      var messageOptional = deserializer.deserialize(rawMessage);
      if (messageOptional.isEmpty()) {
        System.err.println("ERROR: Failed to deserialize RabbitMQ message");
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
        } finally {
          span.end();
        }
      }
    } catch (Exception e) {
      System.err.println("ERROR: Failed to handle RabbitMQ message: " + e.getMessage());
      e.printStackTrace();
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

  private String buildRoutingKey(Object message) {
    String messageType = message.getClass().getSimpleName().toLowerCase();
    String sagaType = "groupcheckout";
    String routingKey = extractRoutingKey(message);

    return String.format("%s.%s.%s", messageType, sagaType, routingKey);
  }

  private String extractRoutingKey(Object message) {
    return message.getClass().getSimpleName();
  }
}
