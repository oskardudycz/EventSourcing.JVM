package io.eventdriven.core.messaging.inmemory;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRouter;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.MessageSource;
import io.eventdriven.core.messaging.MessageValues;
import io.eventdriven.core.messaging.RoutingPattern;
import io.eventdriven.core.messaging.RoutingTarget;
import io.eventdriven.core.processing.MessageProcessor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class InMemoryMessageBus implements MessageBus {
    private record Binding(String queue, String pattern) {}

    private final MessageRouter router;
    private final Set<String> channels = new HashSet<>();
    private final Map<String, List<Binding>> bindings = new LinkedHashMap<>();
    private final Map<String, Map<String, List<Consumer<MessageEnvelope<Object>>>>> queues = new LinkedHashMap<>();
    private final List<Consumer<MessageEnvelope<Object>>> middlewares = new ArrayList<>();
    private final MessageContext context;

    public InMemoryMessageBus(
            MessageContext context,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        this.context = context;
        this.router = new MessageRouter(List.copyOf(routes));
        channels.forEach(channel -> this.channels.add(channel.name()));
        queues.forEach(queue -> bindings.computeIfAbsent(queue.channel(), ignored -> new ArrayList<>())
                .add(new Binding(queue.name(), queue.routingPattern())));
    }

    public <Message> void publish(List<Message> messages) {
        for (var position = 0; position < messages.size(); position++) {
            Object message = messages.get(position);

            var values = context.inheritForPublished(message.getClass().getName(), position);
            var route = routeOf(message, values);
            var published = new MessageEnvelope<>(
                    message, MessageMetadata.of(values, new MessageSource.OnChannel(route.target())));
            for (var middleware : middlewares) middleware.accept(published);

            for (var binding : bindings.getOrDefault(route.target(), List.of())) {
                if (!RoutingPattern.matches(binding.pattern(), route.key())) continue;

                var handlers = queues.getOrDefault(binding.queue(), Map.of())
                        .get(message.getClass().getTypeName());

                if (handlers != null)
                    try (var _ = context.publishing(values)) {
                        var metadata = context.received(values.messageId(), new MessageSource.InQueue(binding.queue()));

                        try (var _ =
                                context.handling("handle " + message.getClass().getSimpleName(), metadata)) {
                            for (var handle : handlers) handle.accept(new MessageEnvelope<>(message, metadata));
                        }
                    }
            }
        }
    }

    @Override
    public MessageBus subscribe(String queue, MessageProcessor... processors) {
        return subscribe(queue, MessageProcessor.canHandle(processors), MessageProcessor.handle(context, processors));
    }

    @Override
    public InMemoryMessageBus subscribe(String queue, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
        for (var type : types)
            queues.computeIfAbsent(queue, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(type.getTypeName(), ignored -> new ArrayList<>())
                    .add(handler);
        return this;
    }

    public void use(Consumer<MessageEnvelope<Object>> middleware) {
        middlewares.add(middleware);
    }

    public void whenCaughtUp(Duration timeout) {}

    private RoutingTarget routeOf(Object message, MessageValues values) {
        try {
            return router.declaredRouteFor(message, channels);
        } catch (RuntimeException unrouted) {
            var published = new MessageEnvelope<>(message, MessageMetadata.of(values, new MessageSource.Unrouted()));
            for (var middleware : middlewares) middleware.accept(published);
            throw unrouted;
        }
    }
}
