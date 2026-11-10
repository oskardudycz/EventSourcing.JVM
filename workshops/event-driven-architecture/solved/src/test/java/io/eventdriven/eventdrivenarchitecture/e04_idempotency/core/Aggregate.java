package io.eventdriven.eventdrivenarchitecture.e04_idempotency.core;

import java.util.List;

public interface Aggregate<Id> {
  Id id();

  List<Object> dequeueUncommittedEvents();
}
