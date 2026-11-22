package io.eventdriven.core.projections;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
public class Counter implements Versioned {
    @EmbeddedId
    private CounterId counterId;

    private int count;
    private long version;

    Counter() {}

    Counter(CounterId counterId) {
        this.counterId = counterId;
    }

    public CounterId getCounterId() {
        return counterId;
    }

    public int getCount() {
        return count;
    }

    void increment() {
        count++;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }
}
