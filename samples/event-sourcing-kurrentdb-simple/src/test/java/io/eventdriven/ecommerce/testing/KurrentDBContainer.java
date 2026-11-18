package io.eventdriven.ecommerce.testing;

import java.util.Set;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class KurrentDBContainer extends GenericContainer<KurrentDBContainer> {
    private static final int PORT = 2113;
    private static final String IMAGE = Set.of("aarch64", "arm64").contains(System.getProperty("os.arch"))
            ? "kurrentplatform/kurrentdb:26.2.3-experimental-arm64-10.0-noble"
            : "kurrentplatform/kurrentdb:26.2.3";
    private static final KurrentDBContainer shared = new KurrentDBContainer();

    public KurrentDBContainer() {
        super(DockerImageName.parse(IMAGE));
        withEnv("KURRENTDB_CLUSTER_SIZE", "1");
        withEnv("KURRENTDB_RUN_PROJECTIONS", "All");
        withEnv("KURRENTDB_START_STANDARD_PROJECTIONS", "true");
        withEnv("KURRENTDB_NODE_PORT", String.valueOf(PORT));
        withEnv("KURRENTDB_INSECURE", "true");
        withEnv("KURRENTDB_ENABLE_ATOM_PUB_OVER_HTTP", "true");
        withExposedPorts(PORT);
        waitingFor(Wait.forHttp("/health/live").forPort(PORT).forStatusCode(204));
    }

    public static synchronized KurrentDBContainer shared() {
        if (!shared.isRunning()) shared.start();

        return shared;
    }

    public String getConnectionString() {
        return "kurrentdb://%s:%d?tls=false".formatted(getHost(), getMappedPort(PORT));
    }
}
