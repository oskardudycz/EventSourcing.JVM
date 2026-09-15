package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AggregateSpecification<Entity extends AbstractAggregate<Event, Id>, Event, Id> {
  private final Supplier<Entity> getEmpty;

  protected AggregateSpecification(Supplier<Entity> getEmpty) {
    this.getEmpty = getEmpty;
  }

  public FactoryWhen given() {
    return new FactoryWhen();
  }

  @SafeVarargs
  public final EntityWhen given(Event... events) {
    var entity = getEmpty.get();

    for (var event : events) {
      entity.when(event);
    }

    entity.dequeueUncommittedEvents();

    return new EntityWhen(entity);
  }

  public final class FactoryWhen {
    public Then when(Function<Entity, Entity> handle) {
      try {
        return new Then(handle.apply(getEmpty.get()), null);
      } catch (Throwable thrown) {
        return new Then(null, thrown);
      }
    }
  }

  public final class EntityWhen {
    private final Entity entity;

    private EntityWhen(Entity entity) {
      this.entity = entity;
    }

    public Then when(Consumer<Entity> handle) {
      try {
        handle.accept(entity);

        return new Then(entity, null);
      } catch (Throwable thrown) {
        return new Then(entity, thrown);
      }
    }
  }

  public final class Then {
    private final Entity entity;
    private final Throwable thrown;

    private Then(Entity entity, Throwable thrown) {
      this.entity = entity;
      this.thrown = thrown;
    }

    @SafeVarargs
    public final Then then(Event... expected) {
      if (thrown != null)
        throw new AssertionError(
          "Expected the When phase to succeed, but it threw %s".formatted(thrown), thrown
        );

      var expectedEvents = Arrays.asList(expected);
      var actualEvents = List.of(entity.dequeueUncommittedEvents());

      assertThat(actualEvents)
        .as("%s", """
          Expected events:
          %s
          Actual uncommitted events:
          %s""".formatted(Transcript.numbered(expectedEvents), Transcript.numbered(actualEvents)))
        .usingRecursiveComparison()
        .isEqualTo(expectedEvents);

      return this;
    }

    public Then thenThrows(Class<? extends Throwable> expected) {
      assertThat(thrown)
        .as("Expected the When phase to throw %s", expected.getSimpleName())
        .isInstanceOf(expected);

      if (entity != null) {
        var enqueuedEvents = List.of(entity.dequeueUncommittedEvents());

        assertThat(enqueuedEvents)
          .as("%s", """
            Expected no events to be enqueued when the When phase throws, but got:
            %s""".formatted(Transcript.numbered(enqueuedEvents)))
          .isEmpty();
      }

      return this;
    }
  }
}
