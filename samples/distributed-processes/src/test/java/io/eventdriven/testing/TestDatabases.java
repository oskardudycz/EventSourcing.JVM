package io.eventdriven.testing;

import com.github.dockerjava.api.model.ContainerPort;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestDatabases {
    private static final String DOCKER_COMPOSE_PROJECT = "eventsourcing";
    private static final String POSTGRESQL_IMAGE = "postgres:18.7-alpine";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:8.4.1";
    static final String RABBITMQ_IMAGE = "rabbitmq:4.4.0-management";

    public static String kurrentDBConnectionString() {
        return kurrentDBConnectionString(DOCKER_COMPOSE_PROJECT);
    }

    static String kurrentDBConnectionString(String dockerComposeProject) {
        return dockerComposePort(dockerComposeProject, "kurrentdb", 2113)
                .map(port -> "kurrentdb://%s:%d?tls=false".formatted(dockerHost(), port))
                .orElseGet(() -> KurrentDBContainer.shared().getConnectionString());
    }

    public static PostgreSQLConnection postgreSQL() {
        return postgreSQL(DOCKER_COMPOSE_PROJECT);
    }

    static PostgreSQLConnection postgreSQL(String dockerComposeProject) {
        return dockerComposePort(dockerComposeProject, "postgres", 5432)
                .map(port -> new PostgreSQLConnection(
                        "jdbc:postgresql://%s:%d/postgres".formatted(dockerHost(), port), "postgres", "postgres"))
                .orElseGet(() -> {
                    var postgreSQL = SharedPostgreSQL.container;

                    return new PostgreSQLConnection(
                            postgreSQL.getJdbcUrl(), postgreSQL.getUsername(), postgreSQL.getPassword());
                });
    }

    public static PostgreSQLConnection newPostgreSQLDatabase() {
        var server = postgreSQL();
        var name = "test_" + UUID.randomUUID().toString().replace("-", "");

        try (var connection = DriverManager.getConnection(server.url(), server.username(), server.password());
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new IllegalStateException("Creating PostgreSQL database %s failed".formatted(name), e);
        }

        return new PostgreSQLConnection(
                server.url().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1"), server.username(), server.password());
    }

    public static String kafkaBootstrapServers() {
        return kafkaBootstrapServers(DOCKER_COMPOSE_PROJECT);
    }

    static String kafkaBootstrapServers(String dockerComposeProject) {
        return dockerComposePort(dockerComposeProject, "kafka", 9092)
                .map(port -> "%s:%d".formatted(dockerHost(), port))
                .orElseGet(() -> SharedKafka.container.getBootstrapServers());
    }

    public static RabbitMQConnection rabbitMQ() {
        return rabbitMQ(DOCKER_COMPOSE_PROJECT);
    }

    static RabbitMQConnection rabbitMQ(String dockerComposeProject) {
        return dockerComposePort(dockerComposeProject, "rabbitmq", 5672)
                .map(port -> new RabbitMQConnection(
                        dockerHost(),
                        port,
                        dockerComposePort(dockerComposeProject, "rabbitmq", 15672)
                                .orElseThrow(),
                        "guest",
                        "guest"))
                .orElseGet(() -> connectionTo(SharedRabbitMQ.container));
    }

    public static RabbitMQContainer newRabbitMQContainer() {
        return new RabbitMQContainer(RABBITMQ_IMAGE);
    }

    public static RabbitMQConnection connectionTo(RabbitMQContainer container) {
        return new RabbitMQConnection(
                container.getHost(),
                container.getAmqpPort(),
                container.getHttpPort(),
                container.getAdminUsername(),
                container.getAdminPassword());
    }

    private static Optional<Integer> dockerComposePort(String dockerComposeProject, String service, int containerPort) {
        return DockerClientFactory.instance()
                .client()
                .listContainersCmd()
                .withLabelFilter(Map.of(
                        "com.docker.compose.project", dockerComposeProject,
                        "com.docker.compose.service", service))
                .exec()
                .stream()
                .flatMap(container -> Arrays.stream(container.getPorts()))
                .filter(port -> Objects.equals(port.getPrivatePort(), containerPort) && port.getPublicPort() != null)
                .map(ContainerPort::getPublicPort)
                .findFirst();
    }

    private static final class SharedPostgreSQL {
        private static final PostgreSQLContainer container = new PostgreSQLContainer(POSTGRESQL_IMAGE);

        static {
            container.start();
        }
    }

    private static final class SharedKafka {
        private static final ConfluentKafkaContainer container = new ConfluentKafkaContainer(
                        DockerImageName.parse(KAFKA_IMAGE))
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

        static {
            container.start();
        }
    }

    private static final class SharedRabbitMQ {
        private static final RabbitMQContainer container = newRabbitMQContainer();

        static {
            container.start();
        }
    }

    private static String dockerHost() {
        return DockerClientFactory.instance().dockerHostIpAddress();
    }
}
