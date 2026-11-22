package io.eventdriven.core.projections;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class LongPollingTests {
    private final AtomicInteger reads = new AtomicInteger();

    @Test
    public void aViewThatHasTheDocumentAnswersAtOnce() {
        var found = LongPolling.retryIfNotFound(() -> read("order"), Duration.ofSeconds(1));

        assertThat(found).contains("order");
        assertThat(reads).hasValue(1);
    }

    @Test
    public void aViewThatCatchesUpWithinTheDeadlineAnswersWithTheDocument() {
        var found =
                LongPolling.retryIfNotFound(() -> reads.get() < 2 ? read(null) : read("order"), Duration.ofSeconds(1));

        assertThat(found).contains("order");
        assertThat(reads).hasValue(3);
    }

    @Test
    public void aViewThatDoesNotCatchUpIsGivenUpOnAtTheDeadline() {
        var startedAt = System.nanoTime();

        var found = LongPolling.retryIfNotFound(() -> read(null), Duration.ofMillis(500));

        var waited = Duration.ofNanos(System.nanoTime() - startedAt);
        assertThat(found).isEmpty();
        assertThat(waited).isBetween(Duration.ofMillis(500), Duration.ofMillis(700));
        assertThat(reads.get()).isGreaterThan(1);
    }

    private Optional<String> read(String document) {
        reads.incrementAndGet();
        return Optional.ofNullable(document);
    }
}
