package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.core;

public sealed interface Message {

  record Command<T>(T message) implements Message {
  }

  record Event<T>(T message) implements Message {
  }

  static <T> Command<T> Send(T command) {
    return new Command<>(command);
  }

  static <T> Event<T> Publish(T event) {
    return new Event<>(event);
  }
}
