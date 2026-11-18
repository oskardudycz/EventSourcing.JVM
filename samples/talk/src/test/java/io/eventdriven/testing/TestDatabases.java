package io.eventdriven.testing;

import com.github.dockerjava.api.model.ContainerPort;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.testcontainers.DockerClientFactory;

public final class TestDatabases {
    private static final String DOCKER_COMPOSE_PROJECT = "eventsourcing";

    public static String kurrentDBConnectionString() {
        return dockerComposePort("kurrentdb", 2113)
                .map("kurrentdb://localhost:%d?tls=false"::formatted)
                .orElseGet(() -> KurrentDBContainer.shared().getConnectionString());
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
