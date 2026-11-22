package io.eventdriven.core.projections;

public interface Versioned {
    long getVersion();

    void setVersion(long version);
}
