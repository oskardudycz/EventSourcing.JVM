package io.eventdriven.distributedprocesses.core.messaging;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Delivers messages from publishers to subscribers, through channels and queues.
 * <p>
 * Commands and events both travel through this bus. The difference is intent, not transport:
 * <ul>
 *   <li>a command asks for something to be done, e.g. {@code CapturePayment}.
 *       It has one intended recipient, which may accept or reject it;</li>
 *   <li>an event records something that already happened, e.g. {@code PaymentCaptured}.
 *       Any number of subscribers may react to it, or none.</li>
 * </ul>
 * The routes decide the channel and the routing key of each published message. A queue bound to
 * the channel gets the message when its pattern matches the key, as a topic exchange does:
 * {@code *} matches one word and {@code #} any number of words. The queue's subscribers for the
 * message's type then handle it. The bus doesn't check that a command has exactly one subscriber;
 * that's up to how the application wires it.
 */
public interface MessageBus {
    /**
     * Publishes messages (commands or events). Middleware sees each message first,
     * then every queue that the message's channel and key reach.
     */
    <Message> void publish(List<Message> messages);

    /**
     * Adds the routes a module publishes with. Returns the bus to allow chaining.
     */
    MessageBus routes(MessageRoutes routes);

    /**
     * Declares a channel that messages can be published to. Returns the bus to allow chaining.
     */
    MessageBus channel(String name);

    /**
     * Binds a queue to a channel for the routing keys that match the pattern.
     */
    MessageBus bind(String queue, String channel, String routingPattern);

    /**
     * Registers a handler for messages of the given type that reach the queue.
     */
    <Message> MessageBus subscribe(String queue, Class<Message> messageClass, Consumer<Message> handler);

    /**
     * Registers middleware called for every published message before subscribers.
     */
    void use(Consumer<Object> middleware);

    /**
     * Waits until the queues this bus consumes have handled or dead-lettered every message published before the call.
     * A bus that isn't running handles nothing, so it returns at once. Fails when the wait takes longer than the timeout.
     */
    void awaitCaughtUp(Duration timeout);
}
