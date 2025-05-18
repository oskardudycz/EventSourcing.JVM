package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core;

public interface Aggregate<Id> {
  Id id();

  Object[] dequeueUncommittedEvents();
}
