package io.eventdriven.testing.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.testing.TestDatabases;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class ExportToRunningGrafanaTests {
    private final StandardEnvironment environment = new StandardEnvironment();

    public ExportToRunningGrafanaTests() {
        environment
                .getPropertySources()
                .addLast(new MapPropertySource(
                        "Config resource 'class path resource [config/application.properties]'",
                        Map.of("management.tracing.export.otlp.enabled", "false")));
    }

    @Test
    public void exportsTracesLogsAndMetricsToTheGrafanaThatRuns() {
        var grafana = new TestDatabases.Grafana("http://localhost:3000", "http://localhost:4318");

        new ExportToRunningGrafana(() -> Optional.of(grafana))
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.export.otlp.enabled"))
                .isEqualTo("true");
        assertThat(environment.getProperty("management.logging.export.otlp.enabled"))
                .isEqualTo("true");
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled"))
                .isEqualTo("true");
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(environment.getProperty("management.opentelemetry.logging.export.otlp.endpoint"))
                .isEqualTo("http://localhost:4318/v1/logs");
        assertThat(environment.getProperty("management.otlp.metrics.export.url"))
                .isEqualTo("http://localhost:4318/v1/metrics");
    }

    @Test
    public void keepsExportOffWhenNoGrafanaRuns() {
        new ExportToRunningGrafana(Optional::empty).postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.export.otlp.enabled"))
                .isEqualTo("false");
    }

    @Test
    public void leavesTheTestsOwnSettingsFirst() {
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "Dynamic Test Properties",
                        Map.of(
                                "management.opentelemetry.tracing.export.otlp.endpoint",
                                "http://localhost:1/v1/traces")));
        var grafana = new TestDatabases.Grafana("http://localhost:3000", "http://localhost:4318");

        new ExportToRunningGrafana(() -> Optional.of(grafana))
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://localhost:1/v1/traces");
    }
}
