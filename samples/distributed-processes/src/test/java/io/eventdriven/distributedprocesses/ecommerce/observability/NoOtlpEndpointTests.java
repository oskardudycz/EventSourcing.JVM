package io.eventdriven.distributedprocesses.ecommerce.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.testing.ApiSpecification;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@AutoConfigureMetrics
@ActiveProfiles("in-memory")
public class NoOtlpEndpointTests extends ApiSpecification {
    private static final int CLOSED_PORT = closedPort();

    @DynamicPropertySource
    static void closedOtlpEndpoint(DynamicPropertyRegistry registry) {
        var endpoint = "http://localhost:%d".formatted(CLOSED_PORT);

        registry.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> endpoint + "/v1/traces");
        registry.add("management.tracing.export.otlp.enabled", () -> "true");
        registry.add("management.logging.export.otlp.enabled", () -> "true");
        registry.add("management.otlp.metrics.export.enabled", () -> "true");
        registry.add("management.opentelemetry.tracing.export.schedule-delay", () -> "100ms");
        registry.add("management.opentelemetry.logging.export.otlp.endpoint", () -> endpoint + "/v1/logs");
        registry.add("management.opentelemetry.logging.export.schedule-delay", () -> "100ms");
        registry.add("management.otlp.metrics.export.url", () -> endpoint + "/v1/metrics");
        registry.add("management.otlp.metrics.export.step", () -> "1s");
    }

    @Autowired
    private SdkTracerProvider tracerProvider;

    @Autowired
    private SdkLoggerProvider loggerProvider;

    @Autowired
    private OtlpMeterRegistry meterRegistry;

    private final List<String> logged = new CopyOnWriteArrayList<>();
    private Appender capture;

    public NoOtlpEndpointTests() {
        super("customers");
    }

    @BeforeEach
    void captureLogs() {
        capture = new AbstractAppender("capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                logged.add("%s %s %s"
                        .formatted(
                                event.getLevel(),
                                event.getLoggerName(),
                                event.getMessage().getFormattedMessage()));
            }
        };
        capture.start();
        ((LoggerContext) LogManager.getContext(false))
                .getConfiguration()
                .getRootLogger()
                .addAppender(capture, Level.WARN, null);
    }

    @AfterEach
    void stopCapturingLogs() {
        ((LoggerContext) LogManager.getContext(false))
                .getConfiguration()
                .getRootLogger()
                .removeAppender("capture");
        capture.stop();
    }

    @Test
    public void noOtlpEndpointLogsNoConnectionErrors() {
        PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(UUID.randomUUID())
                        .withProduct(UUID.randomUUID(), 2)
                        .checkedOut());

        tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        loggerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        meterRegistry.close();

        assertThat(logged)
                .noneMatch(line -> line.contains("io.opentelemetry")
                        || line.contains("io.micrometer.registry.otlp")
                        || line.contains("Connection refused")
                        || line.contains("Failed to export"));
    }

    private static int closedPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
