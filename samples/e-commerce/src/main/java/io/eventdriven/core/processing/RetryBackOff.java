package io.eventdriven.core.processing;

import java.time.Duration;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

public final class RetryBackOff {
    private RetryBackOff() {}

    public static BackOff exponential(Duration initialInterval) {
        var backOff = new ExponentialBackOff(initialInterval.toMillis(), 2);
        backOff.setMaxAttempts(2);
        backOff.setJitter(initialInterval.toMillis() / 2);
        return backOff;
    }
}
