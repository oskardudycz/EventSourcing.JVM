package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class FunctionalTools {
  public static class FoldLeft<Entity, Event> implements Collector<Event, AtomicReference<Entity>, Entity> {
    private final Supplier<Entity> getInitial;
    private final BiFunction<Entity, Event, Entity> accumulator;

    public FoldLeft(Supplier<Entity> getInitial, BiFunction<Entity, Event, Entity> accumulator) {
      this.getInitial = getInitial;
      this.accumulator = accumulator;
    }

    public static <Entity, Event> Entity reduce(
      Event[] events,
      Entity initial,
      BiFunction<Entity, Event, Entity> accumulator
    ) {
      return Arrays.stream(events).collect(foldLeft(() -> initial, accumulator));
    }

    public static <Entity, Event> FoldLeft<Entity, Event> foldLeft(
      Supplier<Entity> getInitial,
      BiFunction<Entity, Event, Entity> accumulator
    ) {
      return new FoldLeft<>(getInitial, accumulator);
    }

    @Override
    public Supplier<AtomicReference<Entity>> supplier() {
      return () -> new AtomicReference<>(getInitial.get());
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

