package io.eventdriven.eventdrivenarchitecture.e04_idempotency.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InMemoryMessageBus implements MessageBus {
  private final Map<String, List<Consumer<Object>>> handlers = new LinkedHashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();

  public <Message> void publish(List<Message> messages) {
    for (Object message : messages) {

      for (var middleware : middlewares)
        middleware.accept(message);

      var messageHandlers = handlers.get(message.getClass().getTypeName());

      if (messageHandlers != null)
        for (var handle : messageHandlers) {
          handle.accept(message);
        }
    }
  }

  public <Message> MessageBus subscribe(Class<Message> messageClass, Consumer<Message> handler) {
    handlers.compute(messageClass.getTypeName(), (messageType, consumers) -> {
      if (consumers == null)
        consumers = new ArrayList<>();

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
}
