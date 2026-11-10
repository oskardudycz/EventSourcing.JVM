package io.eventdriven.eventdrivenarchitecture.e05_consistency.core;

import java.util.List;
import java.util.function.Consumer;

/**
 * Delivers messages from publishers to subscribers.
 * <p>
 * Commands and events both travel through this bus. The difference is intent, not transport:
 * <ul>
 *   <li>a command asks for something to be done, e.g. {@code RecordGuestCheckoutCompletion}.
 *       It has one intended recipient, which may accept or reject it;</li>
 *   <li>an event records something that already happened, e.g. {@code GuestCheckedOut}.
 *       Any number of subscribers may react to it, or none.</li>
 * </ul>
 * The bus delivers each message to every subscriber registered for its type. It doesn't check
 * that a command has exactly one subscriber; that's up to how the application wires it.
 */
public interface MessageBus {
  /**
   * Publishes messages (commands or events). Middleware sees each message first,
   * then every subscriber registered for the message's type.
   */
  <Message> void publish(List<Message> messages);

  /**
   * Registers a handler for messages of the given type. Returns the bus to allow chaining.
   */
  <Message> MessageBus subscribe(Class<Message> messageClass, Consumer<Message> handler);

  /**
   * Registers middleware called for every published message before subscribers.
   */
  void use(Consumer<Object> middleware);
}
