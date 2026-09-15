package io.eventdriven.distributedprocesses.core.messaging;

import java.util.function.Consumer;

// subscription only — publishing a module's internal events is appending them
public interface InternalEventBus {
  <Event> InternalEventBus subscribe(Class<Event> type, Consumer<Event> handler);

  InternalEventBus use(Consumer<Object> middleware);
}
