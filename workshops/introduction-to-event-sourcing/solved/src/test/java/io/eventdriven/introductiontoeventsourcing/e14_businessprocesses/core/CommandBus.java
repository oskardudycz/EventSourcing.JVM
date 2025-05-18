package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CommandBus {
  private final Map<String, Consumer<Object>> handlers = new LinkedHashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();

  public void send(Object[] commands) {
    for (Object command : commands) {
      var commandHandler = handlers.get(command.getClass().getTypeName());

      for (var middleware : middlewares)
        middleware.accept(command);

      if (commandHandler != null) {
        commandHandler.accept(command);
      }
    }
  }

  public <Command> CommandBus handle(Class<Command> commandClass, Consumer<Command> handler) {
    handlers.compute(commandClass.getTypeName(), (commandType, consumer) ->
      consumer != null ? consumer : event -> handler.accept(commandClass.cast(event)));

    return this;
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }
}
