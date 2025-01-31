package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.StreamType;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

public class MongoEventSubscriptionService<TDocument> {
  protected final MongoCollection<TDocument> streamsCollection;
  private final Function<String, List<? extends Bson>> filterSubscription;
  private final Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>> extractEvents;
  protected final ExecutorService executorService;

  public MongoEventSubscriptionService(
    MongoCollection<TDocument> streamsCollection,
    Function<String, List<? extends Bson>> filterSubscription,
    Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>> extractEvents
  ) {
    this(
      streamsCollection,
      filterSubscription,
      extractEvents,
      Executors.newSingleThreadExecutor()
    );
  }

  public MongoEventSubscriptionService(
    MongoCollection<TDocument> streamsCollection,
    Function<String, List<? extends Bson>> filterSubscription,
    Function<ChangeStreamDocument<TDocument>, List<EventEnvelope>> extractEvents,
    ExecutorService executorService
  ) {
    this.streamsCollection = streamsCollection;
    this.filterSubscription = filterSubscription;
    this.extractEvents = extractEvents;
    this.executorService = executorService;
  }

  public <Type> EventSubscription subscribe(
    Class<Type> streamType,
    Consumer<EventEnvelope> messageHandler
  ) {
    return subscribe(streamType, messageHandler, null, BatchingPolicy.ofSize(1));
  }

  public <Type> EventSubscription subscribe(
    Class<Type> streamType,
    Consumer<List<EventEnvelope>> batchHandler,
    BatchingPolicy policy
  ) {
    return subscribe(streamType, null, batchHandler, policy);
  }

  protected <Type> EventSubscription subscribe(
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
            Thread.sleep(1000);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    });

    return new EventSubscription(queue, messageHandler, batchHandler, policy);
  }

  private <Type> void listenToChanges(Class<Type> streamType, BlockingQueue<EventEnvelope> queue) {
    var watch = streamsCollection.watch(filterSubscription.apply(StreamType.of(streamType)));

    try (var cursor = new MongoEventStreamCursor<>(watch, extractEvents)) {
      while (cursor.hasNext()) {
        var events = cursor.next();
        for (EventEnvelope event : events) {
          queue.put(event);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
