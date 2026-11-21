package io.eventdriven.distributedprocesses.core.messaging;

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
    private final Map<String, Map<String, List<Consumer<Object>>>> queues = new LinkedHashMap<>();
    private final List<Consumer<Object>> middlewares = new ArrayList<>();
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
            for (var middleware : middlewares) middleware.accept(message);

            var route = router.declaredRouteFor(message, channels);

            var values = context.inheritForPublished(message.getClass().getName(), position);

            for (var binding : bindings.getOrDefault(route.target(), List.of())) {
                if (!RoutingPattern.matches(binding.pattern(), route.key())) continue;

                var handlers = queues.getOrDefault(binding.queue(), Map.of())
                        .get(message.getClass().getTypeName());

                if (handlers != null)
                    try (var _ = context.publishing(values);
                            var _ = context.consuming(
                                    "handle " + message.getClass().getSimpleName(), values.messageId())) {
                        for (var handle : handlers) {
                            handle.accept(message);
                        }
                    }
            }
        }
    }

    public <Message> InMemoryMessageBus subscribe(
            String queue, Class<Message> messageClass, Consumer<Message> handler) {
        queues.computeIfAbsent(queue, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(messageClass.getTypeName(), ignored -> new ArrayList<>())
                .add(message -> handler.accept(messageClass.cast(message)));
        return this;
    }

    public void use(Consumer<Object> middleware) {
        middlewares.add(middleware);
    }

    public void awaitCaughtUp(Duration timeout) {}
}
