package io.eventdriven.testing.tracing;

import io.eventdriven.testing.TestDatabases;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Sends the telemetry of the tests to the Docker Compose Grafana when it runs, so a test run can be looked at there.
 * The setting sits below the test's own properties and above the application's properties files.
 */
public class ExportToRunningGrafana implements EnvironmentPostProcessor, Ordered {
    private final Supplier<Optional<TestDatabases.Grafana>> runningGrafana;

    public ExportToRunningGrafana() {
        this(TestDatabases::runningGrafana);
    }

    ExportToRunningGrafana(Supplier<Optional<TestDatabases.Grafana>> runningGrafana) {
        this.runningGrafana = runningGrafana;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        runningGrafana.get().ifPresent(grafana -> {
            var endpoint = grafana.otlpHttpUrl();
            var export = new MapPropertySource(
                    "Export to the running Grafana",
                    Map.of(
                            "management.tracing.export.otlp.enabled",
                            "true",
                            "management.logging.export.otlp.enabled",
                            "true",
                            "management.otlp.metrics.export.enabled",
                            "true",
                            "management.opentelemetry.tracing.export.otlp.endpoint",
                            endpoint + "/v1/traces",
                            "management.opentelemetry.logging.export.otlp.endpoint",
                            endpoint + "/v1/logs",
                            "management.otlp.metrics.export.url",
                            endpoint + "/v1/metrics"));
            var sources = environment.getPropertySources();
            var firstConfigFile = sources.stream()
                    .filter(source -> source.getName().startsWith("Config resource"))
                    .findFirst();

            if (firstConfigFile.isPresent())
                sources.addBefore(firstConfigFile.get().getName(), export);
            else sources.addLast(export);
        });
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
