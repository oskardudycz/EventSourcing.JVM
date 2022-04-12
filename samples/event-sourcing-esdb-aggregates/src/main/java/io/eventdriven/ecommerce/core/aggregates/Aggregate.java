package io.eventdriven.ecommerce.core.aggregates;

public interface Aggregate<T> {
  T getId();
  int getVersion();

  Object[] dequeueUncommittedEvents();
}
