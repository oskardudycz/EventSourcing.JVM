package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventSubscription implements AutoCloseable {
  private final BlockingQueue<EventEnvelope> queue;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread consumerThread;

  EventSubscription(
    BlockingQueue<EventEnvelope> queue,
    EventSubscriptionSettings settings
  ) {
    this.queue = queue;
    this.consumerThread = new Thread(() ->
      consumeWithPolicy(settings));
    this.consumerThread.start();
  }

  private void consumeWithPolicy(EventSubscriptionSettings settings) {
    var policy = settings.policy();
    var handler = settings.handler();

    if (handler == null) {
      throw new IllegalArgumentException("At least one handler must be provided");
    }

    List<EventEnvelope> batch = new ArrayList<>(policy.batchSize());

    while (running.get()) {
      try {
        var event = queue.poll(policy.maxWait().toMillis(), TimeUnit.MILLISECONDS);
        if (event != null) {
          batch.add(event);
          queue.drainTo(batch, policy.batchSize() - batch.size());
        }

        if (!batch.isEmpty() && (batch.size() >= policy.batchSize() || event == null)) {
          handler.accept(new ArrayList<>(batch));
          batch.clear();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  @Override
  public void close() {
    running.set(false);
    consumerThread.interrupt();
  }
}
