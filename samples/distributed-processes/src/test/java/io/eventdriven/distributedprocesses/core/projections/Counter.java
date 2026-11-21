package io.eventdriven.distributedprocesses.core.projections;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
class Counter implements Versioned {
    @EmbeddedId
    private CounterId counterId;

    private int count;
    private long version;

    Counter() {}

    Counter(CounterId counterId) {
        this.counterId = counterId;
    }

    CounterId getCounterId() {
        return counterId;
    }

    int getCount() {
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
