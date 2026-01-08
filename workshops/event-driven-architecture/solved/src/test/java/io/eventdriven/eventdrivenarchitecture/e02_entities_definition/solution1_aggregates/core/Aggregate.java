package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.core;

import java.util.List;

public interface Aggregate<Id> {
  Id id();

  List<Object> dequeueUncommittedEvents();
}
