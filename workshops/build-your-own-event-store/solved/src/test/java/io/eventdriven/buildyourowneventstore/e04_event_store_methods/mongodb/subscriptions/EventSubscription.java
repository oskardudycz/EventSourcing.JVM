package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EventSubscription implements AutoCloseable {
  private final BlockingQueue<EventEnvelope> queue;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final EventSubscriptionSettings settings;
  private final BiConsumer<EventSubscriptionSettings, Consumer<EventEnvelope>> listenToChanges;
  private final ExecutorService executor;

  EventSubscription(
    EventSubscriptionSettings settings,
    BiConsumer<EventSubscriptionSettings, Consumer<EventEnvelope>> listenToChanges
  ) {
    if (settings.handler() == null) {
      throw new IllegalArgumentException("At least one handler must be provided");
    }
    this.queue = new LinkedBlockingQueue<>();
    this.settings = settings;
    this.listenToChanges = listenToChanges;
    this.executor = Executors.newFixedThreadPool(2);
  }

  public void start() {
    executor.submit(this::listen);
    executor.submit(this::consume);
  }

  private void listen() {
    while (running.get()) {
      try {
        listenToChanges.accept(settings, e -> {
          try {
            queue.put(e);
          } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
          }
        });
      } catch (Exception ex) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }

  private void consume() {
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
    try {
      executor.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
