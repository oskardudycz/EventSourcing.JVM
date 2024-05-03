package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.mutable.solution1;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public abstract class Aggregate<Event> {
  protected UUID id;

  private final Queue<Object> uncommittedEvents = new LinkedList<>();

  public UUID id() {
    return id;
  }

  public Object[] dequeueUncommittedEvents() {
    var dequeuedEvents = uncommittedEvents.toArray();

    uncommittedEvents.clear();

    return dequeuedEvents;
  }

  public abstract void evolve(Event event);

  protected void enqueue(Event event) {
    uncommittedEvents.add(event);
    evolve(event);
  }
}
