package io.eventdriven.distributedprocesses.core.aggregates;

public interface Aggregate<Id> {
  Id id();

  Object[] dequeueUncommittedEvents();
}
