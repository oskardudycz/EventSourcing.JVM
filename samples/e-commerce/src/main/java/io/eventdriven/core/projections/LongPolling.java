package io.eventdriven.core.projections;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Reads a view that may not have caught up yet with a write the caller just made. It reads again with a growing,
 * jittered delay until the view has the document or the deadline passes, so the caller does not have to retry.
 * See <a href="https://event-driven.io/en/long_polling_and_eventual_consistency/">Long-polling, how to make our async
 * API synchronous</a>.
 */
public final class LongPolling {
    public static final Duration DEADLINE = Duration.ofSeconds(1);
    private static final Duration DELAY = Duration.ofMillis(100);

    private LongPolling() {}

    public static <Document> Optional<Document> retryIfNotFound(Supplier<Optional<Document>> find) {
        return retryIfNotFound(find, DEADLINE);
    }

    public static <Document> Optional<Document> retryIfNotFound(Supplier<Optional<Document>> find, Duration deadline) {
        return retryUntil(find, document -> true, deadline);
    }

    /**
     * Reads until the view has a document that is ready, or the deadline passes. Then it returns the last document it
     * read, ready or not, so the caller can tell a view that is behind from a document that doesn't exist.
     */
    public static <Document> Optional<Document> retryUntil(
            Supplier<Optional<Document>> find, Predicate<Document> isReady, Duration deadline) {
        var giveUpAt = System.nanoTime() + deadline.toNanos();

        for (var attempt = 0; ; attempt++) {
            var found = find.get();
            var remaining = giveUpAt - System.nanoTime();

            if (found.filter(isReady).isPresent() || remaining <= 0) return found;

            var delay = (DELAY.toNanos() << Math.min(attempt, 10))
                    + ThreadLocalRandom.current().nextLong(DELAY.toNanos());

            try {
                Thread.sleep(Duration.ofNanos(Math.min(delay, remaining)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return found;
            }
        }
    }
}
