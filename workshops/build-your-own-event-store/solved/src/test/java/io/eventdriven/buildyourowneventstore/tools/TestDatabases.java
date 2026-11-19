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
        return mongoDBConnectionString(DOCKER_COMPOSE_PROJECT, databaseName);
    }

    static String mongoDBConnectionString(String dockerComposeProject, String databaseName) {
        return dockerComposePort(dockerComposeProject, "mongodb", 27017)
                .map(port -> "mongodb://%s:%d/%s?replicaSet=rs0&directConnection=true"
                        .formatted(dockerHost(), port, databaseName))
                .orElseGet(() -> MongoDBTestContainer.connectionString(databaseName));
    }

    public static Settings postgreSQL() {
        return postgreSQL(DOCKER_COMPOSE_PROJECT);
    }

    static Settings postgreSQL(String dockerComposeProject) {
        return dockerComposePort(dockerComposeProject, "postgres", 5432)
                .map(port -> new Settings(
                        "jdbc:postgresql://%s:%d/postgres".formatted(dockerHost(), port), "postgres", "postgres"))
                .orElseGet(PostgreSQLTestContainer::settings);
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

    private static String dockerHost() {
        return DockerClientFactory.instance().dockerHostIpAddress();
    }
}
