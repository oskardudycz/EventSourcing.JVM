package io.eventdriven.distributedprocesses.core.messaging;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public class MessageRouter {
    private final List<MessageRoutes.Registration> registrations;
    private final Function<Object, RoutingTarget> convention;

    public MessageRouter(List<MessageRoutes> messageRoutes, Function<Object, RoutingTarget> convention) {
        this.registrations = messageRoutes.stream()
                .flatMap(registered -> registered.getRegistrations().stream())
                .toList();
        this.convention = convention;
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

        var conventional = convention.apply(message);
        return new RoutingTarget(
                target != null ? target : conventional.target(), key != null ? key : conventional.key());
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

    public static @Nullable String firstRecordComponent(Object message) {
        if (!(message instanceof Record record)) return null;

        var components = record.getClass().getRecordComponents();
        if (components.length == 0) return null;

        try {
            var value = components[0].getAccessor().invoke(record);
            return value != null ? value.toString() : null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Can't read " + components[0].getName() + " of "
                            + record.getClass().getName(),
                    e);
        }
    }
}
