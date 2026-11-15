package io.eventdriven.buildyourowneventstore.tools.mongodb;

import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

public final class MongoDBTestContainer {
  private static final MongoDBContainer container =
    new MongoDBContainer(DockerImageName.parse("mongo:9.0.8")).withReplicaSet();

  public static synchronized String connectionString(String databaseName) {
    if (!container.isRunning())
      container.start();

    return container.getReplicaSetUrl(databaseName);
  }
}
