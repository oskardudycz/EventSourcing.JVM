package io.eventdriven.buildyourowneventstore.tools;

import com.github.dockerjava.api.model.ContainerPort;
import io.eventdriven.buildyourowneventstore.tools.mongodb.MongoDBTestContainer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.testcontainers.DockerClientFactory;

public final class TestDatabases {
    private static final String DOCKER_COMPOSE_PROJECT = "eventsourcing";

    public static String mongoDBConnectionString(String databaseName) {
        return dockerComposePort("mongodb", 27017)
                .map(port ->
                        "mongodb://localhost:%d/%s?replicaSet=rs0&directConnection=true".formatted(port, databaseName))
                .orElseGet(() -> MongoDBTestContainer.connectionString(databaseName));
    }

    public static Settings postgreSQL() {
        return dockerComposePort("postgres", 5432)
                .map(port ->
                        new Settings("jdbc:postgresql://localhost:%d/postgres".formatted(port), "postgres", "postgres"))
                .orElseGet(PostgreSQLTestContainer::settings);
    }

    private static Optional<Integer> dockerComposePort(String service, int containerPort) {
        return DockerClientFactory.instance()
                .client()
                .listContainersCmd()
                .withLabelFilter(Map.of(
                        "com.docker.compose.project", DOCKER_COMPOSE_PROJECT,
                        "com.docker.compose.service", service))
                .exec()
                .stream()
                .flatMap(container -> Arrays.stream(container.getPorts()))
                .filter(port -> Objects.equals(port.getPrivatePort(), containerPort) && port.getPublicPort() != null)
                .map(ContainerPort::getPublicPort)
                .findFirst();
    }
}
