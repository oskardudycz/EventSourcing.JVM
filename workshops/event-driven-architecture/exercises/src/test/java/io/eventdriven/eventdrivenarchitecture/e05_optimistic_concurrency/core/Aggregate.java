package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core;

import java.util.List;

public interface Aggregate<Id> {
  Id id();

  List<Object> dequeueUncommittedEvents();
}
