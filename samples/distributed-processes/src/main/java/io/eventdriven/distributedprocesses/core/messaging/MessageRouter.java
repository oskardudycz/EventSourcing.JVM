package io.eventdriven.distributedprocesses.core.messaging;

import java.util.List;
import java.util.Set;

public class MessageRouter {
    private final List<MessageRoutes.Registration> registrations;

    public MessageRouter(List<MessageRoutes> messageRoutes) {
        this.registrations = messageRoutes.stream()
                .flatMap(registered -> registered.getRegistrations().stream())
                .toList();
    }

    public RoutingTarget routeFor(Object message) {
        String target = null;
        String key = null;

        for (var registration : registrations) {
            if (target != null && key != null) break;

            if (!registration.matches().test(message)) continue;

            var route = registration.route().apply(message);
            if (target == null) target = route.target();
            if (key == null) key = route.key();
        }

        return new RoutingTarget(target, key);
    }

    public RoutingTarget declaredRouteFor(Object message, Set<String> declaredChannels) {
        var route = routeFor(message);

        if (route.target() == null)
            throw new IllegalStateException(
                    "No route for message type %s".formatted(message.getClass().getName()));

        if (!declaredChannels.contains(route.target()))
            throw new IllegalStateException("Channel '%s' of message type %s is not declared"
                    .formatted(route.target(), message.getClass().getName()));

        return route;
    }
}
