package io.eventdriven.eventdrivenarchitecture.e05_consistency.core;

import java.util.List;

public interface Aggregate<Id> {
  Id id();

  List<Object> dequeueUncommittedEvents();
}
