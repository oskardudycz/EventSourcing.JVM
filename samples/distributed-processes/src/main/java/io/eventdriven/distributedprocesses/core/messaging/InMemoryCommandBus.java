package io.eventdriven.distributedprocesses.core.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InMemoryCommandBus implements CommandBus {
  private final Map<Class<?>, Consumer<Object>> handlers = new HashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();

  @Override
  public <Command> void send(Command... commands) {
    for (var command : commands) {
      var handler = handlers.get(command.getClass());

      if (handler == null) {
        throw new IllegalStateException(
          "No handler registered for command type: " + command.getClass().getName()
        );
      }

      for (var middleware : middlewares) {
        middleware.accept(command);
      }

      handler.accept(command);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Command> CommandBus handle(Class<Command> type, Consumer<Command> handler) {
    if (handlers.containsKey(type)) {
      throw new IllegalStateException(
        "Handler already registered for command type: " + type.getName()
      );
    }

    handlers.put(type, (Consumer<Object>) handler);

    return this;
  }

  @Override
  public CommandBus use(Consumer<Object> middleware) {
    middlewares.add(middleware);

    return this;
  }
}
