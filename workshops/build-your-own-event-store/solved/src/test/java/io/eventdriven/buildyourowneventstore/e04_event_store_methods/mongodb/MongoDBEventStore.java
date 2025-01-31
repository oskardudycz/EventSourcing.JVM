package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb;

import com.mongodb.client.MongoClient;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.EventStore;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.MongoDBEventStoreWithEventAsDocument;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.MongoDBEventStoreWithStreamAsDocument;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions.EventSubscription;
import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.subscriptions.EventSubscriptionSettings;

public interface MongoDBEventStore extends EventStore {
  EventSubscription subscribe(EventSubscriptionSettings settings);

  enum Storage {
    EventAsDocument,
    StoreAsDocument,
  }

  static MongoDBEventStore with(
    Storage storage,
    MongoClient mongoClient,
    String databaseName
  ) {
    switch (storage) {
      case EventAsDocument -> {
        return new MongoDBEventStoreWithEventAsDocument(mongoClient, databaseName);
      }
      case StoreAsDocument -> {
        return new MongoDBEventStoreWithStreamAsDocument(mongoClient, databaseName);
      }
      default ->
        throw new IllegalStateException("Unexpected value: " + storage);
    }
  }
}
