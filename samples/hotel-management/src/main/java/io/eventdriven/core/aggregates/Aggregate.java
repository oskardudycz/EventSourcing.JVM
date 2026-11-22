package io.eventdriven.core.aggregates;

public interface Aggregate<Id> {
    Id id();

    Object[] dequeueUncommittedEvents();
}
