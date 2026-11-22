package io.eventdriven.core.processing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

public class RetryBackOffTests {
    @Test
    public void retriesTwiceWithDoublingJitteredDelaysThenStops() {
        var backOff = RetryBackOff.exponential(Duration.ofSeconds(1));

        for (var i = 0; i < 100; i++) {
            var execution = backOff.start();

            assertThat(execution.nextBackOff()).as("first retry").isBetween(1000L, 1500L);
            assertThat(execution.nextBackOff())
                    .as("second retry, doubled, jitter doubled too")
                    .isBetween(1000L, 3000L);
            assertThat(execution.nextBackOff()).as("no third retry").isEqualTo(BackOffExecution.STOP);
        }
    }
}
