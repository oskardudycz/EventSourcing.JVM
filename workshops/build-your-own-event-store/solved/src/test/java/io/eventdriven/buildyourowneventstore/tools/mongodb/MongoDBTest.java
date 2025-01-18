package io.eventdriven.buildyourowneventstore.tools.mongodb;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class MongoDBTest {
  protected static MongoClient mongoClient;
  protected static String databaseName;

  @BeforeAll
  public void setupConnection() {
    mongoClient = NativeMongoConfig.createClient();
    databaseName = StackWalker
      .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
      .getCallerClass()
      .getName()
      .replace(".", "-");
  }

  @AfterAll
  public void tearDown() {
    mongoClient.close();
  }
}
