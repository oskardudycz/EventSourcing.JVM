package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamType;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class MongoEventSubscriptionService {
  private final ExecutorService executorService;
  private final MongoCollection<EventEnvelope> eventsCollection;

  public MongoEventSubscriptionService(MongoCollection<EventEnvelope> eventsCollection, ExecutorService executorService) {
    this.eventsCollection = eventsCollection;
    this.executorService = executorService;
  }

  public <Type> EventSubscription<Type> subscribe(
    Class<Type> streamType,
    Consumer<EventEnvelope> messageHandler,
    Consumer<List<EventEnvelope>> batchHandler,
    BatchingPolicy policy) {

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
    Pattern pattern = Pattern.compile("^" + Pattern.quote(streamName));

    var pipeline = List.of(
      Aggregates.match(
        Filters.and(
          Filters.eq("operationType", "insert"),
          Filters.regex("fullDocument.metadata.streamName", pattern)
        )
      )
    );

    try (WatchCursor<ChangeStreamDocument<EventEnvelope>> cursor = watchChanges(pipeline)) {
      while (cursor.hasNext()) {
        var change = cursor.next();
        var event = extractEvent(change);
        if (event != null) {
          queue.put(event);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private WatchCursor<ChangeStreamDocument<EventEnvelope>> watchChanges(List<?> pipeline) {
    @SuppressWarnings("unchecked")
    List<Bson> bsonPipeline = (List<Bson>) pipeline;

    var cursor = eventsCollection.watch(bsonPipeline)
      .maxAwaitTime(10, TimeUnit.SECONDS)
      .cursor();

    return new WatchCursor<>() {
      @Override
      public boolean hasNext() {
        return cursor.hasNext();
      }

      @Override
      public ChangeStreamDocument<EventEnvelope> next() {
        return cursor.next();
      }

      @Override
      public void close() {
        cursor.close();
      }
    };
  }

  private EventEnvelope extractEvent(ChangeStreamDocument<EventEnvelope> change) {
    var operationType = change.getOperationType();

    if (operationType == null) {
      return null;
    }

    return change.getFullDocument();
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
