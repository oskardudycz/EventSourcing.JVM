package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

public sealed interface SagaResult {

  record Command<T>(T message) implements SagaResult {
  }

  record Event<T>(T message) implements SagaResult {
  }

  record None() implements SagaResult {
  }

  static <T> Command<T> Send(T command) {
    return new Command<>(command);
  }

  static <T> Event<T> Publish(T event) {
    return new Event<>(event);
  }

  None Ignore = new None();
}
