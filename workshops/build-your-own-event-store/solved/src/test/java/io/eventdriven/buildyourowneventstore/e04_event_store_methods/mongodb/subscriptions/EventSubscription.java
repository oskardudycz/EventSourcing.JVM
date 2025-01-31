package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class EventSubscription implements AutoCloseable {
  private final BlockingQueue<EventEnvelope> queue;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private final EventSubscriptionSettings settings;
  private final Supplier<WatchCursor<List<EventEnvelope>>> listen;
  private final ExecutorService executor;

  public EventSubscription(
    Supplier<WatchCursor<List<EventEnvelope>>> listen,
    EventSubscriptionSettings settings
  ) {
    if (settings.handler() == null) {
      throw new IllegalArgumentException("At least one handler must be provided");
    }
    this.queue = new LinkedBlockingQueue<>();
    this.settings = settings;
    this.listen = listen;
    this.executor = Executors.newFixedThreadPool(2);
  }

  public void start() {
    executor.submit(this::listen);
    executor.submit(this::consume);
  }

  private void listen() {
    try (var cursor = listen.get()) {
      while (running.get() && cursor.hasNext()) {
        var events = cursor.next();
        for (var event : events) {
          if (!running.get()) break;
          queue.put(event);
        }
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      if (running.get()) {
        throw new RuntimeException(e);
      }
    }
  }

  private void consume() {
    var policy = settings.policy();
    var handler = settings.handler();
    List<EventEnvelope> batch = new ArrayList<>(policy.batchSize());

    try {
      while (running.get()) {
        var event = queue.poll(policy.maxWait().toMillis(), TimeUnit.MILLISECONDS);
        if (event != null) {
          batch.add(event);
          queue.drainTo(batch, policy.batchSize() - batch.size());
        }

        if (!batch.isEmpty() && (batch.size() >= policy.batchSize() || event == null)) {
          handler.accept(new ArrayList<>(batch));
          batch.clear();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      // Process remaining batch if any
      if (!batch.isEmpty()) {
        handler.accept(batch);
      }
    }
  }

  @Override
  public void close() {
    running.set(false);
    executor.shutdownNow(); // Interrupt any blocking operations
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        System.err.println("Executor did not terminate in time");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
