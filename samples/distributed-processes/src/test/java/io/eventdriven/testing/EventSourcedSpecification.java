package io.eventdriven.testing;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class EventSourcedSpecification<Entity, Event> {
  private final Supplier<Entity> getDefault;
  private final BiFunction<Entity, Event, Entity> evolve;

  protected EventSourcedSpecification(Supplier<Entity> getDefault, BiFunction<Entity, Event, Entity> evolve) {
    this.getDefault = getDefault;
    this.evolve = evolve;
  }

  public EventSourcedSpecificationBuilder<Entity, Event> given(Supplier<Event[]> getEvents) {
    return new EventSourcedSpecificationBuilder(this, getEvents);
  }

  protected class EventSourcedSpecificationBuilder<Entity, Event> {

    private final EventSourcedSpecification<Entity, Event> specification;
    private final Supplier<Event[]> getEvents;
    private Function<Entity, Event[]> handle;

    public EventSourcedSpecificationBuilder(EventSourcedSpecification<Entity, Event> specification, Supplier<Event[]> getEvents) {
      this.specification = specification;
      this.getEvents = getEvents;
    }

    public EventSourcedSpecificationBuilder<Entity, Event> when(Function<Entity, Event[]> handle) {
      this.handle = handle;

      return this;
    }

    public EventSourcedSpecificationBuilder<Entity, Event> then(Consumer<Event[]> then) {
      var events = this.getEvents.get();

      Entity current = this.specification.getDefault.get();

      for (var event : events) {
        current = this.specification.evolve.apply(current, event);
      }

      var newEvents = this.handle.apply(current);

      then.accept(newEvents);

      return this;
    }
  }
}
