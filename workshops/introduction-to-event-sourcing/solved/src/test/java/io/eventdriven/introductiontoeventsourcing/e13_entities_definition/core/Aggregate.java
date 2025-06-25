package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core;

public interface Aggregate<Id> {
  Id id();

  Object[] dequeueUncommittedEvents();
}
