package io.eventdriven.ecommerce.core.aggregates;

public interface Aggregate<Id> {
  Id id();
  int version();

  Object[] dequeueUncommittedEvents();
}
