package io.eventdriven.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class TestDatabasesTests {
    private static final String IMAGE = "postgres:18.7-alpine";

    private final String dockerComposeProject = "test-" + UUID.randomUUID();

    @Test
    public void newPostgreSQLDatabaseDoesNotSeeTheTablesOfTheSharedOne() throws SQLException {
        var table = "shared_" + UUID.randomUUID().toString().replace("-", "");
        execute(TestDatabases.postgreSQL(), "CREATE TABLE %s (id int)".formatted(table));

        var database = TestDatabases.newPostgreSQLDatabase();

        assertThat(tablesOf(database)).doesNotContain(table);
        assertThat(tablesOf(TestDatabases.postgreSQL())).contains(table);
    }

    private static void execute(PostgreSQLConnection database, String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(database.url(), database.username(), database.password());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> tablesOf(PostgreSQLConnection database) throws SQLException {
        try (var connection = DriverManager.getConnection(database.url(), database.username(), database.password());
                var tables = connection.getMetaData().getTables(null, "public", "%", new String[] {"TABLE"})) {
            var names = new ArrayList<String>();
            while (tables.next()) names.add(tables.getString("TABLE_NAME"));
            return names;
        }
    }

    @Test
    public void usesTheKurrentDBThatDockerComposeRuns() {
        try (var kurrentDB = new KurrentDBContainer().withLabels(dockerComposeLabels("kurrentdb"))) {
            kurrentDB.start();

            assertThat(TestDatabases.kurrentDBConnectionString(dockerComposeProject))
                    .isEqualTo(kurrentDB.getConnectionString());
        }
    }

    @Test
    public void usesThePostgreSQLThatDockerComposeRuns() {
        try (var postgreSQL = new PostgreSQLContainer(IMAGE)
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("postgres")
                .withLabels(dockerComposeLabels("postgres"))) {
            postgreSQL.start();

            assertThat(TestDatabases.postgreSQL(dockerComposeProject))
                    .isEqualTo(new PostgreSQLConnection(
                            "jdbc:postgresql://%s:%d/postgres"
                                    .formatted(postgreSQL.getHost(), postgreSQL.getFirstMappedPort()),
                            "postgres",
                            "postgres"));
        }
    }

    @Test
    public void usesTheKafkaThatDockerComposeRuns() {
        try (var kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.4.1"))
                .withLabels(dockerComposeLabels("kafka"))) {
            kafka.start();

            assertThat(TestDatabases.kafkaBootstrapServers(dockerComposeProject))
                    .isEqualTo(kafka.getBootstrapServers());
        }
    }

    @Test
    public void usesTheRabbitMQThatDockerComposeRuns() {
        try (var rabbitMQ = TestDatabases.newRabbitMQContainer().withLabels(dockerComposeLabels("rabbitmq"))) {
            rabbitMQ.start();

            assertThat(TestDatabases.rabbitMQ(dockerComposeProject)).isEqualTo(TestDatabases.connectionTo(rabbitMQ));
        }
    }

    private Map<String, String> dockerComposeLabels(String service) {
        return Map.of("com.docker.compose.project", dockerComposeProject, "com.docker.compose.service", service);
    }
}
