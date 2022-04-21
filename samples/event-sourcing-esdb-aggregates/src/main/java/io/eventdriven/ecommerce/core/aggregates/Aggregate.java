package io.eventdriven.ecommerce.core.aggregates;

public interface Aggregate<Id> {
  Id id();

  Object[] dequeueUncommittedEvents();
}
