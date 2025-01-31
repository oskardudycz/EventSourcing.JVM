package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamType;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public abstract class BaseSubscriptionService {
  protected final ExecutorService executorService;
  protected interface WatchCursor<T> extends Iterator<T>, AutoCloseable {}

  protected BaseSubscriptionService(ExecutorService executorService) {
    this.executorService = executorService;
  }

  protected abstract List<?> createPipeline(String streamName);

  protected abstract EventEnvelope extractEvent(Object change);

  protected abstract WatchCursor<?> watchChanges(List<?> pipeline);

  public <Type> EventSubscription<Type> subscribe(
    Class<Type> streamType,
    Consumer<EventEnvelope> messageHandler,
    Consumer<List<EventEnvelope>> batchHandler,
    BatchingPolicy policy
  ) {

    if (messageHandler == null && batchHandler == null) {
      throw new IllegalArgumentException("At least one handler must be provided");
    }

    BlockingQueue<EventEnvelope> queue = new LinkedBlockingQueue<>();

    executorService.execute(() -> {
      while (true) {
        try {
          listenToChanges(streamType, queue);
        } catch (Exception ex) {
          try {
            Thread.sleep(5000);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    });

    return new EventSubscription<>(queue, messageHandler, batchHandler, policy);
  }

  private <Type> void listenToChanges(Class<Type> streamType, BlockingQueue<EventEnvelope> queue) {
    String streamName = StreamType.of(streamType);
    var pipeline = createPipeline(streamName);

    try (var cursor = watchChanges(pipeline)) {
      while (cursor.hasNext()) {
        var change = cursor.next();
        var event = extractEvent(change);
        if (event != null) {
          queue.put(event);
        }
      }
    } catch (Exception e) {
      System.err.println(e.getMessage());
      throw new RuntimeException(e);
    }
  }

  public <Type> EventSubscription<Type> subscribe(
    Class<Type> streamType,
    Consumer<EventEnvelope> messageHandler) {
    return subscribe(streamType, messageHandler, null, BatchingPolicy.ofSize(1));
  }

  public <Type> EventSubscription<Type> subscribe(
    Class<Type> streamType,
    Consumer<List<EventEnvelope>> batchHandler,
    BatchingPolicy policy) {
    return subscribe(streamType, null, batchHandler, policy);
  }
}
