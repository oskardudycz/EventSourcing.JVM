package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.micrometer.tracing.Tracer;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.RabbitMessageBaggageHandler.setHeadersFromBaggage;

@Component
public class RabbitMQMessageBus implements MessageBus {
  private final Map<String, List<Consumer<Object>>> messageHandlers = new ConcurrentHashMap<>();
  private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
  private final Tracer tracer;
  private final VersionedMessageConverter messageConverter;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.exchange:hotel-financial}")
  private String exchangeName;

  @Value("${app.rabbitmq.publish-timeout:10s}")
  private Duration publishTimeout;

  public RabbitMQMessageBus(Tracer tracer, VersionedMessageConverter messageConverter) {
    this.tracer = tracer;
    this.messageConverter = messageConverter;
  }

  public <Message> void publish(List<Message> messages) {
    for (Object message : messages) {
      for (var middleware : middlewares)
        middleware.accept(message);

      var span = tracer.nextSpan().name("messagebus.publish").start();
      try (var _ = tracer.withSpan(span)) {
        var correlationData = new CorrelationData();

        rabbitTemplate.convertAndSend(exchangeName, message.getClass().getName(), message, amqpMessage -> {
          setHeadersFromBaggage(amqpMessage, tracer);
          return amqpMessage;
        }, correlationData);

        waitForConfirm(correlationData);
      } catch (RuntimeException e) {
        span.error(e);
        throw e;
      } finally {
        span.end();
      }
    }
  }

  @RabbitListener(
    queues = "${app.rabbitmq.exchange:hotel-financial}-saga",
    containerFactory = "rabbitListenerContainerFactory"
  )
  public void handleMessage(Message rawMessage) {
    Object message = messageConverter.read(rawMessage);

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

  private void waitForConfirm(CorrelationData correlationData) {
    CorrelationData.Confirm confirm;
    try {
      confirm = correlationData.getFuture().get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw new AmqpException("Failed to confirm the message", e.getCause());
    } catch (TimeoutException e) {
      throw new AmqpTimeoutException("No publisher confirm from RabbitMQ within " + publishTimeout, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AmqpException("Interrupted while waiting for the publisher confirm", e);
    }

    if (!confirm.isAck())
      throw new AmqpException("RabbitMQ did not accept the message: " + confirm.getReason());

    var returned = correlationData.getReturned();
    if (returned != null)
      throw new AmqpException("RabbitMQ could not route the message: " + returned.getReplyText());
  }
}
