package io.eventdriven.ecommerce.core.aggregates;

import java.util.LinkedList;
import java.util.Queue;

public abstract class AbstractAggregate<Id> {
  protected Id id;
  protected int version;

  private final Queue uncommittedEvents = new LinkedList<>();

  public Id id() {
    return id;
  }

  public int version() {
    return version;
  }

  public Object[] dequeueUncommittedEvents() {
    var dequeuedEvents = uncommittedEvents.toArray();

    uncommittedEvents.clear();

    return dequeuedEvents;
  }

  protected void enqueue(Object event) {
    uncommittedEvents.add(event);
    version++;
  }
}
