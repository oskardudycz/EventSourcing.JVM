package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RouteRecordingMessageBus implements MessageBus {
    public final List<MessageRoutes> routes = new ArrayList<>();
    public final List<String> channels = new ArrayList<>();

    @Override
    public <Message> void publish(List<Message> messages) {}

    @Override
    public MessageBus routes(MessageRoutes moduleRoutes) {
        routes.add(moduleRoutes);
        return this;
    }

    @Override
    public MessageBus channel(String name) {
        channels.add(name);
        return this;
    }

    @Override
    public MessageBus bind(String queue, String channel, String routingPattern) {
        return this;
    }

    @Override
    public <Message> MessageBus subscribe(String queue, Class<Message> messageClass, Consumer<Message> handler) {
        return this;
    }

    @Override
    public void use(Consumer<Object> middleware) {}

    @Override
    public void awaitCaughtUp(Duration timeout) {}
}
