package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class EventSubscription implements AutoCloseable {
  private final BlockingQueue<EventEnvelope> queue;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final Thread consumerThread;

  EventSubscription(
    BlockingQueue<EventEnvelope> queue,
    Consumer<EventEnvelope> messageHandler,
    Consumer<List<EventEnvelope>> batchHandler,
    BatchingPolicy policy
  ) {
    this.queue = queue;
    this.consumerThread = new Thread(() ->
      consumeWithPolicy(messageHandler, batchHandler, policy));
    this.consumerThread.start();
  }

  private void consumeWithPolicy(Consumer<EventEnvelope> messageHandler,
                                 Consumer<List<EventEnvelope>> batchHandler,
                                 BatchingPolicy policy) {
    List<EventEnvelope> batch = new ArrayList<>(policy.batchSize());

    while (running.get()) {
      try {
        EventEnvelope event = queue.poll(policy.maxWait().toMillis(), TimeUnit.MILLISECONDS);
        if (event != null) {
          if (messageHandler != null) {
            messageHandler.accept(event);
          }

          if (batchHandler != null) {
            batch.add(event);
            queue.drainTo(batch, policy.batchSize() - batch.size());
          }
        }

        if (batchHandler != null && !batch.isEmpty() &&
          (batch.size() >= policy.batchSize() || event == null)) {
          batchHandler.accept(new ArrayList<>(batch));
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
