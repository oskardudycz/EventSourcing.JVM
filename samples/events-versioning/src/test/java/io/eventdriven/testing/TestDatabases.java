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
        return kurrentDBConnectionString(DOCKER_COMPOSE_PROJECT);
    }

    static String kurrentDBConnectionString(String dockerComposeProject) {
        return dockerComposePort(dockerComposeProject, "kurrentdb", 2113)
                .map(port -> "kurrentdb://%s:%d?tls=false".formatted(dockerHost(), port))
                .orElseGet(() -> KurrentDBContainer.shared().getConnectionString());
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
