package io.eventdriven.testing;

import com.github.dockerjava.api.model.ContainerPort;
import io.eventdriven.testing.eventstore.kurrentdb.KurrentDBContainer;
import io.eventdriven.testing.messaging.rabbitmq.RabbitMQConnection;
import io.eventdriven.testing.postgresql.PostgreSQLConnection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public final class TestDatabases {
    private static final String DOCKER_COMPOSE_PROJECT = "eventsourcing";
    private static final String POSTGRESQL_IMAGE = "postgres:18.7-alpine";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:8.4.1";
    private static final String RABBITMQ_IMAGE = "rabbitmq:4.4.0-management";
    private static final String GRAFANA_IMAGE = "grafana/otel-lgtm:0.41.0";

    public record Grafana(String url, String otlpHttpUrl) {}

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

    public static void addProjectionsDatabase(DynamicPropertyRegistry registry, PostgreSQLConnection postgreSQL) {
        registry.add("spring.datasource.url", postgreSQL::url);
        registry.add("spring.datasource.username", postgreSQL::username);
        registry.add("spring.datasource.password", postgreSQL::password);
    }

    public static void addPostgreSQLEventStore(DynamicPropertyRegistry registry, PostgreSQLConnection postgreSQL) {
        registry.add("eventstore.postgresql.url", postgreSQL::url);
        registry.add("eventstore.postgresql.username", postgreSQL::username);
        registry.add("eventstore.postgresql.password", postgreSQL::password);
    }

    public static void addRabbitMQ(DynamicPropertyRegistry registry, RabbitMQConnection rabbitMQ) {
        registry.add("spring.rabbitmq.host", rabbitMQ::host);
        registry.add("spring.rabbitmq.port", rabbitMQ::port);
        registry.add("spring.rabbitmq.username", rabbitMQ::username);
        registry.add("spring.rabbitmq.password", rabbitMQ::password);
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

    // Grafana LGTM from Docker Compose when it runs, with the same dashboards, or a container of its own.
    public static Grafana grafana() {
        return runningGrafana()
                .orElseGet(() -> new Grafana(
                        SharedGrafana.container.getGrafanaHttpUrl(), SharedGrafana.container.getOtlpHttpUrl()));
    }

    public static Optional<Grafana> runningGrafana() {
        return dockerComposePort(DOCKER_COMPOSE_PROJECT, "otel-lgtm", 3000)
                .flatMap(grafanaPort -> dockerComposePort(DOCKER_COMPOSE_PROJECT, "otel-lgtm", 4318)
                        .map(otlpPort -> new Grafana(
                                "http://localhost:%d".formatted(grafanaPort),
                                "http://localhost:%d".formatted(otlpPort))));
    }

    public static ConfluentKafkaContainer newKafkaContainer() {
        return new ConfluentKafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");
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

    private static final class SharedGrafana {
        private static final LgtmStackContainer container = new LgtmStackContainer(GRAFANA_IMAGE)
                .withCopyFileToContainer(MountableFile.forHostPath("observability/dashboards"), "/otel-lgtm/dashboards")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("observability/dashboards.yaml"),
                        "/otel-lgtm/grafana/conf/provisioning/dashboards/e-commerce.yaml")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("observability/alerting/ecommerce.yaml"),
                        "/otel-lgtm/grafana/conf/provisioning/alerting/e-commerce.yaml");

        static {
            container.start();
        }
    }

    private static final class SharedPostgreSQL {
        private static final PostgreSQLContainer container = new PostgreSQLContainer(POSTGRESQL_IMAGE);

        static {
            container.start();
        }
    }

    private static final class SharedKafka {
        private static final ConfluentKafkaContainer container = newKafkaContainer();

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
