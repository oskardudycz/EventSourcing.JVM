package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution1_aggregates.core;

public interface Aggregate<Id> {
  Id id();

  Object[] dequeueUncommittedEvents();
}
