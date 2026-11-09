package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRouter;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageRoutes;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RoutingTarget;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.setMetadataHeaders;

@Component
public class RabbitMQMessageBus implements MessageBus {
  private static final Logger log = LoggerFactory.getLogger(RabbitMQMessageBus.class);

  private final Map<String, List<Consumer<Object>>> messageHandlers = new ConcurrentHashMap<>();
  private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
  private final Tracer tracer;
  private final VersionedMessageConverter messageConverter;
  private final RabbitTemplate rabbitTemplate;
  private final RabbitMQMessageBusProperties properties;
  private final MessageRouter router;

  public RabbitMQMessageBus(
    Tracer tracer,
    VersionedMessageConverter messageConverter,
    RabbitTemplate rabbitTemplate,
    RabbitMQMessageBusProperties properties,
    ObjectProvider<MessageRoutes> messageRoutes
  ) {
    this.tracer = tracer;
    this.messageConverter = messageConverter;
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
    this.router = new MessageRouter(
      messageRoutes.orderedStream().toList(),
      message -> new RoutingTarget(properties.exchange(), message.getClass().getName())
    );
  }

  public <Message> void publish(List<Message> messages) {
    for (Object message : messages) {
      for (var middleware : middlewares)
        middleware.accept(message);

      var route = router.routeFor(message);
      var correlationData = new CorrelationData();

      rabbitTemplate.convertAndSend(route.target(), route.key(), message, amqpMessage -> {
        setMetadataHeaders(amqpMessage, tracer);
        return amqpMessage;
      }, correlationData);

      waitForConfirm(correlationData);
    }
  }

  @RabbitListener(queues = "${app.rabbitmq.exchange}-saga")
  public void handleMessage(Message rawMessage) {
    Object message = messageConverter.read(rawMessage);

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

  private void waitForConfirm(CorrelationData correlationData) {
    CorrelationData.Confirm confirm;
    try {
      confirm = correlationData.getFuture().get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw new AmqpException("Failed to confirm the message", e.getCause());
    } catch (TimeoutException e) {
      throw new AmqpTimeoutException("No publisher confirm from RabbitMQ within " + properties.publishTimeout(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AmqpException("Interrupted while waiting for the publisher confirm", e);
    }

    if (!confirm.ack())
      throw new AmqpException("RabbitMQ did not accept the message: " + confirm.reason());

    var returned = correlationData.getReturned();
    if (returned != null)
      throw new AmqpException("RabbitMQ could not route the message: " + returned.getReplyText());
  }
}
