package io.eventdriven.uniqueness.testing;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgreSQLTestContainer {
  private static final PostgreSQLContainer container =
    new PostgreSQLContainer(DockerImageName.parse("postgres:18.7-alpine"));

  public static synchronized Settings settings() {
    if (!container.isRunning())
      container.start();

    return new Settings(
      "jdbc:postgresql://%s:%d/%s".formatted(
        container.getHost(),
        container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
        container.getDatabaseName()
      ),
      container.getUsername(),
      container.getPassword()
    );
  }
}
