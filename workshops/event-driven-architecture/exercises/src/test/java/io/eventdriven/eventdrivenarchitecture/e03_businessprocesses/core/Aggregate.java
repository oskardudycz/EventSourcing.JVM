package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

public interface Aggregate<Id> {
  Id id();

  Object[] dequeueUncommittedEvents();
}
