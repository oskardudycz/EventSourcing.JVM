package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.eventdriven.buildyourowneventstore.e01_storage.EventStore;

public class MongoDBEventStore implements EventStore {
  private final MongoClient mongoClient;
  private final MongoDatabase database;

  public MongoDBEventStore(MongoClient mongoClient, String databaseName) {
    this.mongoClient = mongoClient;
    database = this.mongoClient.getDatabase(databaseName);
  }

  @Override
  public void init() {
    database.createCollection("streams", new CreateCollectionOptions());

    var collection = database.getCollection("streams");
    collection.createIndex(Indexes.ascending("streamName"), new IndexOptions().unique(true));
  }
}
