package io.eventdriven.testing.time;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class MovableClock implements Supplier<OffsetDateTime> {
    private final AtomicReference<OffsetDateTime> now =
            new AtomicReference<>(OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));

    public void set(OffsetDateTime time) {
        now.set(time);
    }

    public void moveBy(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public OffsetDateTime get() {
        return now.get();
    }
}
