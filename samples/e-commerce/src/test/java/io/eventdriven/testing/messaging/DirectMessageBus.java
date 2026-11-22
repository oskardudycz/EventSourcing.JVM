package io.eventdriven.testing.messaging;

import io.eventdriven.core.messaging.MessageBus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class DirectMessageBus implements MessageBus {
    private record Subscriber(Class<?> messageClass, Consumer<Object> handler) {}

    private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final List<CompletableFuture<Void>> pending = new CopyOnWriteArrayList<>();

    @Override
    public <Message> void publish(List<Message> messages) {
        for (var message : messages) {
            middlewares.forEach(middleware -> middleware.accept(message));
            subscribers.stream()
                    .filter(subscriber -> subscriber.messageClass().isInstance(message))
                    .forEach(subscriber -> subscriber.handler().accept(message));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Message> MessageBus subscribe(String queue, Class<Message> messageClass, Consumer<Message> handler) {
        subscribers.add(new Subscriber(messageClass, message -> handler.accept((Message) message)));

        return this;
    }

    @Override
    public void use(Consumer<Object> middleware) {
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
