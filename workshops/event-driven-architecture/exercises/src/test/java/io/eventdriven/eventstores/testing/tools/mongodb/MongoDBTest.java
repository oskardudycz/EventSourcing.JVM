package io.eventdriven.eventstores.testing.tools.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.eventdriven.eventstores.mongodb.MongoDBEventStore;
import io.eventdriven.eventstores.mongodb.config.NativeMongoConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MongoDBTest {
  protected static MongoClient mongoClient;
  private static final int maxDatabaseLength = 63;

  @BeforeAll
  public void setupConnection() {
    mongoClient = NativeMongoConfig.createClient();
  }

  protected MongoDatabase getFreshDatabase() {
    var databaseName = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
      .walk(stack ->
        stack
          .map(StackWalker.StackFrame::getDeclaringClass)
          .filter(clazz -> !MongoDBTest.class.equals(clazz))
          .findFirst()
          .map(Class::getName)
          .orElseThrow()
      )
      .replace("io.eventdriven.eventstores.", "")
      .replace("io.eventdriven.introductiontoeventsourcing.", "")
      .replace(".", "-")
      .toLowerCase();

    if (databaseName.length() >= maxDatabaseLength)
      databaseName = databaseName.substring(0, maxDatabaseLength);

    return getFreshDatabase(databaseName);
  }

  protected MongoDatabase getFreshDatabase(String databaseName) {
    mongoClient.getDatabase(databaseName).drop();

    return mongoClient.getDatabase(databaseName);
  }

  protected Stream<MongoDBEventStore.Storage> mongoEventStorages() {
    return Stream.of(
      MongoDBEventStore.Storage.EventAsDocument,
      MongoDBEventStore.Storage.StoreAsDocument
    );
  }

  protected MongoDBEventStore getMongoEventStoreWith(MongoDBEventStore.Storage storage) {
    var mongoDatabase = getFreshDatabase();

    var eventStore = MongoDBEventStore.with(storage, mongoClient, mongoDatabase.getName());

    eventStore.init();

    return eventStore;
  }

  @AfterAll
  public void tearDown() {
    mongoClient.close();
  }
}
