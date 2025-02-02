package io.eventdriven.eventdrivenarchitecture.e04_getting_state_from_events.mongodb.immutable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class FunctionalTools {
  public static class FoldLeft<Entity, Event> implements Collector<Event, AtomicReference<Entity>, Entity> {
    private final Supplier<Entity> getEmpty;
    private final BiFunction<Entity, Event, Entity> accumulator;

    public FoldLeft(Supplier<Entity> getEmpty, BiFunction<Entity, Event, Entity> accumulator) {
      this.getEmpty = getEmpty;
      this.accumulator = accumulator;
    }

    public static <Entity, Event> FoldLeft<Entity, Event> foldLeft(
      Supplier<Entity> getEmpty,
      BiFunction<Entity, Event, Entity> accumulator
    ) {
      return new FoldLeft<>(getEmpty, accumulator);
    }


    @Override
    public Supplier<AtomicReference<Entity>> supplier() {
      return () -> new AtomicReference<>(getEmpty.get());
    }

    @Override
    public BiConsumer<AtomicReference<Entity>, Event> accumulator() {
      return (wrapper, event) -> wrapper.set(accumulator.apply(wrapper.get(), event));
    }

    @Override
    public BinaryOperator<AtomicReference<Entity>> combiner() {
      return (left, right) -> {
        left.set(right.get());
        return left;
      };
    }

    @Override
    public Function<AtomicReference<Entity>, Entity> finisher() {
      return AtomicReference::get;
    }

    @Override
    public Set<Characteristics> characteristics() {
      return new HashSet<>();
    }
  }

  public static <T, S> Collector<T, ?, LinkedHashMap<S, List<T>>> groupingByOrdered(Function<? super T, S> selector) {
    return Collectors.groupingBy(
      selector,
      LinkedHashMap::new,
      Collectors.mapping(p -> p, toList()));
  }
}
