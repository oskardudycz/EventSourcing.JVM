package io.eventdriven.testing;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class EventSourcedSpecification<Entity, Event> {
  private final Supplier<Entity> getDefault;
  private final BiFunction<Entity, Event, Entity> evolve;

  protected EventSourcedSpecification(Supplier<Entity> getDefault, BiFunction<Entity, Event, Entity> evolve) {
    this.getDefault = getDefault;
    this.evolve = evolve;
  }

  public EventSourcedSpecificationBuilder<Entity, Event> given() {
    return new EventSourcedSpecificationBuilder(this, () -> new Object[0]);
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
      then.accept(this.handle.apply(replay()));

      return this;
    }

    public EventSourcedSpecificationBuilder<Entity, Event> thenThrows(Class<? extends Throwable> expected) {
      var current = replay();

      assertThatThrownBy(() -> this.handle.apply(current)).isInstanceOf(expected);

      return this;
    }

    private Entity replay() {
      Entity current = this.specification.getDefault.get();

      for (var event : this.getEvents.get()) {
        current = this.specification.evolve.apply(current, event);
      }

      return current;
    }

    public EventSourcedSpecificationBuilder<Entity, Event> then(Event... expectedEvents) {
      return then(events -> {
        assertEquals(expectedEvents.length, events.length);
        for (var i = 0; i < events.length; i++) {
          assertEquals(expectedEvents[i], events[i]);
        }
      });
    }
  }
}
