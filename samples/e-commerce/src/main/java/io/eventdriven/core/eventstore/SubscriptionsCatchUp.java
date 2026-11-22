package io.eventdriven.core.eventstore;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SubscriptionsCatchUp {
    private record Watched(Set<String> typeNames, Subscription subscription) {}

    private final Map<String, Checkpoint> lastAppended = new ConcurrentHashMap<>();
    private final List<Watched> subscriptions = new CopyOnWriteArrayList<>();

    public void subscribed(Set<String> typeNames, Subscription subscription) {
        subscriptions.add(new Watched(typeNames, subscription));
    }

    public void appended(String typeName, Checkpoint checkpoint) {
        lastAppended.merge(typeName, checkpoint, (current, next) -> next.compareTo(current) > 0 ? next : current);
    }

    public void whenCaughtUp(Duration timeout) {
        var deadline = System.nanoTime() + timeout.toNanos();

        for (var watched : subscriptions) {
            var target = watched.typeNames().stream()
                    .map(lastAppended::get)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());

            if (target.isEmpty()) continue;

            watched.subscription()
                    .whenProcessed(target.get(), Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        }
    }
}
