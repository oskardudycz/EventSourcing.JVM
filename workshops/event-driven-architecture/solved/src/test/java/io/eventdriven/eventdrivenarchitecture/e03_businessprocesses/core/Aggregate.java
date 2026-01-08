package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import java.util.List;

public interface Aggregate<Id> {
  Id id();

  List<Object> dequeueUncommittedEvents();
}
