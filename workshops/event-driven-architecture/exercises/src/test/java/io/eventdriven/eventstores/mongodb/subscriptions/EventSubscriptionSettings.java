package io.eventdriven.eventstores.mongodb.subscriptions;

import io.eventdriven.eventstores.StreamType;
import io.eventdriven.eventstores.mongodb.events.EventEnvelope;

import java.util.List;
import java.util.function.Consumer;

public class EventSubscriptionSettings {
  private String streamType;
  private Consumer<List<EventEnvelope>> handler;
  private BatchingPolicy policy = BatchingPolicy.DEFAULT;

  private EventSubscriptionSettings() {
  }

  public static EventSubscriptionSettings get() {
    return new EventSubscriptionSettings();
  }

  public <T> EventSubscriptionSettings filterWithStreamType(Class<T> streamClass) {
    return filterWithStreamType(StreamType.of(streamClass));
  }

  public EventSubscriptionSettings filterWithStreamType(String streamType) {
    this.streamType = streamType;

    return this;
  }

  public EventSubscriptionSettings handleBatch(Consumer<List<EventEnvelope>> handler) {
    return handleBatch(handler, BatchingPolicy.DEFAULT);
  }

  public EventSubscriptionSettings handleBatch(Consumer<List<EventEnvelope>> handler, BatchingPolicy policy) {
    this.handler = handler;
    this.policy = policy;

    return this;
  }

  public EventSubscriptionSettings handleSingle(Consumer<EventEnvelope> handler) {
    this.handler = (events) -> {
      events.forEach(handler);
    };

    return this;
  }

  public String streamType() {
    return streamType;
  }

  public Consumer<List<EventEnvelope>> handler() {
    return handler;
  }

  public BatchingPolicy policy() {
    return policy;
  }
}
