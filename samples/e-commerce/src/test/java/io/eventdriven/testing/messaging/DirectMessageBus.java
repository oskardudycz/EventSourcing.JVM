package io.eventdriven.testing.messaging;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.MessageSource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class DirectMessageBus implements MessageBus {
    private record Subscriber(String queue, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {}

    private final List<Consumer<MessageEnvelope<Object>>> middlewares = new CopyOnWriteArrayList<>();
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<Void>> pending = new CopyOnWriteArrayList<>();

    @Override
    public <Message> void publish(List<Message> messages) {
        for (var message : messages) {
            var messageId = UUID.randomUUID().toString();
            var published = new MessageEnvelope<Object>(
                    message,
                    new MessageMetadata(
                            messageId, messageId, messageId, null, null, new MessageSource.OnChannel("direct")));
            middlewares.forEach(middleware -> middleware.accept(published));
            subscribers.stream()
                    .filter(subscriber -> subscriber.types().stream().anyMatch(type -> type.isInstance(message)))
                    .forEach(subscriber -> subscriber
                            .handler()
                            .accept(new MessageEnvelope<>(
                                    message,
                                    new MessageMetadata(
                                            messageId,
                                            messageId,
                                            messageId,
                                            null,
                                            null,
                                            new MessageSource.InQueue(subscriber.queue())))));
        }
    }

    @Override
    public MessageBus subscribe(String queue, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
        subscribers.add(new Subscriber(queue, List.copyOf(types), handler));

        return this;
    }

    @Override
    public void use(Consumer<MessageEnvelope<Object>> middleware) {
        middlewares.add(middleware);
    }

    public void publishLater(Object message, Duration delay) {
        pending.add(CompletableFuture.runAsync(
                () -> publish(List.of(message)),
                CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)));
    }

    @Override
    public void whenCaughtUp(Duration timeout) {
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Messages published later were not delivered in time", e);
        }
    }
}
