package io.eventdriven.buildyourowneventstore.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class TestDatabasesTests {
    private final String dockerComposeProject = "test-" + UUID.randomUUID();

    @Test
    public void usesTheMongoDBThatDockerComposeRuns() {
        try (var mongoDB =
                new MongoDBContainer(DockerImageName.parse("mongo:9.0.8")).withLabels(dockerComposeLabels("mongodb"))) {
            mongoDB.start();

            assertThat(TestDatabases.mongoDBConnectionString(dockerComposeProject, "test"))
                    .startsWith("mongodb://%s:%d/test".formatted(mongoDB.getHost(), mongoDB.getMappedPort(27017)));
        }
    }

    @Test
    public void usesThePostgreSQLThatDockerComposeRuns() {
        try (var postgreSQL = new PostgreSQLContainer(DockerImageName.parse("postgres:18.7-alpine"))
                .withLabels(dockerComposeLabels("postgres"))) {
            postgreSQL.start();

            assertThat(TestDatabases.postgreSQL(dockerComposeProject).connectionString())
                    .isEqualTo("jdbc:postgresql://%s:%d/postgres"
                            .formatted(postgreSQL.getHost(), postgreSQL.getMappedPort(5432)));
        }
    }

    private Map<String, String> dockerComposeLabels(String service) {
        return Map.of("com.docker.compose.project", dockerComposeProject, "com.docker.compose.service", service);
    }
}
