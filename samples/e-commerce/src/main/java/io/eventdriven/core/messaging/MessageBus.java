package io.eventdriven.core.messaging;

import io.eventdriven.core.processing.MessageProcessor;
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
 * The routes decide the channel and the routing key of each published message. The channels, the
 * queues bound to them and the routes are given to the bus when it is created. A queue bound to
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
     * Registers a handler for the messages of the given types that reach the queue.
     */
    MessageBus subscribe(String queue, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler);

    /**
     * Registers processors for the messages that reach the queue. For each message, every processor that can handle it
     * runs, in order.
     */
    default MessageBus subscribe(String queue, MessageProcessor... processors) {
        return subscribe(queue, MessageProcessor.canHandle(processors), MessageProcessor.handle(processors));
    }

    /**
     * Registers a handler for the messages of the given type that reach the queue.
     */
    default <Message> MessageBus subscribe(String queue, Class<Message> messageClass, Consumer<Message> handler) {
        return subscribe(
                queue, List.of(messageClass), envelope -> handler.accept(messageClass.cast(envelope.message())));
    }

    /**
     * Registers middleware called for every published message before subscribers.
     */
    void use(Consumer<Object> middleware);

    /**
     * Waits until the queues this bus consumes have handled or dead-lettered every message published before the call.
     * A bus that isn't running handles nothing, so it returns at once. Fails when the wait takes longer than the timeout.
     */
    void whenCaughtUp(Duration timeout);

    default void whenCaughtUp() {
        whenCaughtUp(Duration.ofSeconds(30));
    }
}
