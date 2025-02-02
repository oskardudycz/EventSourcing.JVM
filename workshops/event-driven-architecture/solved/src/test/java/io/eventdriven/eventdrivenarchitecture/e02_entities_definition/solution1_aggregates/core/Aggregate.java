package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.core;

public interface Aggregate<Id> {
  Id id();

  Object[] dequeueUncommittedEvents();
}
