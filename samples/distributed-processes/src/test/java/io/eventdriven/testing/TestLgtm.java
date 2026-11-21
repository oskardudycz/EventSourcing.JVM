package io.eventdriven.testing;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.utility.MountableFile;

public final class TestLgtm {
    private static final LgtmStackContainer shared = new LgtmStackContainer("grafana/otel-lgtm:0.41.0")
            .withStartupTimeout(Duration.ofMinutes(3))
            // Loki refuses writes while the disk is over 90% full; test logs need no write-ahead log
            .withEnv("LOKI_EXTRA_ARGS", "-ingester.wal-enabled=false")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("observability/tempo-config.yaml"), "/otel-lgtm/tempo-config.yaml")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("observability/purchase-flow-dashboard.json"),
                    "/otel-lgtm/purchase-flow-dashboard.json")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("observability/purchase-flow-dashboards.yaml"),
                    "/otel-lgtm/grafana/conf/provisioning/dashboards/purchase-flow.yaml");

    public static synchronized LgtmStackContainer shared() {
        if (!shared.isRunning()) {
            shared.start();
            var api = new LgtmApi(shared);
            await().atMost(Duration.ofMinutes(2)).until(() -> api.statusOf(shared.getLokiUrl() + "/ready") == 200);
        }

        return shared;
    }
}
