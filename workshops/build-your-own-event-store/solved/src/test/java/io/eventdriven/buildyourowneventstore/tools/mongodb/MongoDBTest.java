package io.eventdriven.buildyourowneventstore.tools.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MongoDBTest {
  protected static MongoClient mongoClient;

  @BeforeAll
  public void setupConnection() {
    mongoClient = NativeMongoConfig.createClient();
  }

  protected MongoDatabase getFreshDatabase() {
    var databaseName = StackWalker
      .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
      .getCallerClass()
      .getName()
      .replace("io.eventdriven.buildyourowneventstore", "")
      .replace(".", "-")
      .substring(0, 63)
      .toLowerCase();

    return getFreshDatabase(databaseName);
  }

  protected MongoDatabase getFreshDatabase(String databaseName) {
    mongoClient.getDatabase(databaseName).drop();

    return mongoClient.getDatabase(databaseName);
  }

  @AfterAll
  public void tearDown() {
    mongoClient.close();
  }
}
