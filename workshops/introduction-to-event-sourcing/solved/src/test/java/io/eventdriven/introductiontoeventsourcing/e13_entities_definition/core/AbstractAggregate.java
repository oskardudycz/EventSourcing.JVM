package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core;

import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.Aggregate;

import java.util.LinkedList;
import java.util.Queue;

public abstract class AbstractAggregate<Event, Id> implements Aggregate<Id> {
  protected Id id;
  protected int version = -1;

  private final Queue<Object> uncommittedEvents = new LinkedList<>();

  public Id id() {
    return id;
  }

  public Object[] dequeueUncommittedEvents() {
    var dequeuedEvents = uncommittedEvents.toArray();

    uncommittedEvents.clear();

    return dequeuedEvents;
  }

  public abstract void apply(Event event);

  protected void enqueue(Event event) {
    uncommittedEvents.add(event);
    apply(event);
    version++;
  }
}
