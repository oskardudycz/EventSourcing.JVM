package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.entities;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public abstract class Aggregate<Event> {
  protected UUID id;

  private final Queue<Event> uncommittedEvents = new LinkedList<>();

  public UUID id() {
    return id;
  }

  public List<Event> dequeueUncommittedEvents() {
    var dequeuedEvents = uncommittedEvents.stream().toList();

    uncommittedEvents.clear();

    return dequeuedEvents;
  }

  public abstract void evolve(Event event);

  protected void enqueue(Event event) {
    uncommittedEvents.add(event);
    evolve(event);
  }
}
