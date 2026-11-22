package io.eventdriven.testing.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.testing.Transcript;
import java.util.List;
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

    public EventSourcedSpecificationBuilder<Entity, Event> given() {
        return new EventSourcedSpecificationBuilder(this, () -> new Object[0]);
    }

    public EventSourcedSpecificationBuilder<Entity, Event> given(Supplier<Event[]> getEvents) {
        return new EventSourcedSpecificationBuilder(this, getEvents);
    }

    @SafeVarargs
    public final EventSourcedSpecificationBuilder<Entity, Event> given(Event... events) {
        return new EventSourcedSpecificationBuilder(this, () -> events);
    }

    protected class EventSourcedSpecificationBuilder<Entity, Event> {

        private final EventSourcedSpecification<Entity, Event> specification;
        private final Supplier<Event[]> getEvents;
        private Function<Entity, Event[]> handle;

        public EventSourcedSpecificationBuilder(
                EventSourcedSpecification<Entity, Event> specification, Supplier<Event[]> getEvents) {
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

        @SafeVarargs
        public final EventSourcedSpecificationBuilder<Entity, Event> then(Event... expectedEvents) {
            return then(events -> {
                var actualEvents = List.of(events);
                var expected = List.of(expectedEvents);

                assertThat(actualEvents)
                        .as("%s", """
              Expected events:
              %s
              Actual events:
              %s""".formatted(Transcript.numbered(expected), Transcript.numbered(actualEvents)))
                        .usingRecursiveComparison()
                        .isEqualTo(expected);
            });
        }

        public EventSourcedSpecificationBuilder<Entity, Event> thenNothing() {
            return then(events -> assertThat(events).isEmpty());
        }
    }
}
