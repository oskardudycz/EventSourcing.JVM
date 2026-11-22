package io.eventdriven.ecommerce;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.addProductItem;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.checkOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpStatus.ACCEPTED;

import io.eventdriven.testing.api.ApiE2ESpecification;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.grafana.LgtmStackContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@AutoConfigureMetrics
@ActiveProfiles("in-memory")
@Import(InMemorySpansConfiguration.class)
public class OtlpExportTests extends ApiE2ESpecification {
    private static final Duration ingestionTimeout = Duration.ofSeconds(60);
    private static final LgtmStackContainer grafana = new LgtmStackContainer("grafana/otel-lgtm:0.41.0");
    private static final JsonMapper json = JsonMapper.builder().build();
    private static final RestClient http = RestClient.create();

    static {
        grafana.start();
    }

    @DynamicPropertySource
    static void exportToGrafana(DynamicPropertyRegistry registry) {
        var endpoint = grafana.getOtlpHttpUrl();

        registry.add("management.tracing.export.otlp.enabled", () -> "true");
        registry.add("management.logging.export.otlp.enabled", () -> "true");
        registry.add("management.otlp.metrics.export.enabled", () -> "true");
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> endpoint + "/v1/traces");
        registry.add("management.opentelemetry.logging.export.otlp.endpoint", () -> endpoint + "/v1/logs");
        registry.add("management.otlp.metrics.export.url", () -> endpoint + "/v1/metrics");
        registry.add("management.otlp.metrics.export.step", () -> "1s");
    }

    @Autowired
    private SdkTracerProvider tracerProvider;

    @Autowired
    private SdkLoggerProvider loggerProvider;

    @Autowired
    private OtlpMeterRegistry meterRegistry;

    @Test
    public void aCheckoutReachesGrafanaAsATraceWithItsCommandsAndAsMetrics() {
        var customerId = UUID.randomUUID();
        var spans = new CopyOnWriteArrayList<SpanData>();

        given(addProductItem(customerId, UUID.randomUUID(), 2))
                .when(checkOut(customerId, weakETag(0)))
                .then(
                        expectResponse(ACCEPTED),
                        outcome -> spans.addAll(outcome.spans().get()));
        flush();

        var placeOrder = spans.stream()
                .filter(span -> span.getName().equals("command.handle"))
                .filter(span -> "PlaceOrder"
                        .equals(span.getAttributes().asMap().entrySet().stream()
                                .filter(attribute -> attribute.getKey().getKey().equals("eventdriven.command.type"))
                                .map(attribute -> String.valueOf(attribute.getValue()))
                                .findFirst()
                                .orElse(null)))
                .findFirst()
                .orElseThrow();
        await().atMost(ingestionTimeout)
                .untilAsserted(() -> assertThat(traceInTempo(placeOrder.getTraceId()))
                        .contains("command.handle", "PlaceOrder", "eventStore.appendToStream"));
        await().atMost(ingestionTimeout)
                .untilAsserted(() -> assertThat(prometheus(
                                "eventdriven_event_appending_count_total{eventdriven_event_type=\"OrderPlaced\"}"))
                        .isNotEmpty());
        await().atMost(ingestionTimeout)
                .untilAsserted(() -> assertThat(
                                prometheus("command_handle_seconds_count{eventdriven_command_type=\"PlaceOrder\"}"))
                        .isNotEmpty());
    }

    @org.junit.jupiter.api.AfterAll
    static void printNames() {
        System.out.println("METRICS "
                + http.get()
                        .uri(URI.create(grafana.getPrometheusHttpUrl() + "/api/v1/label/__name__/values"))
                        .retrieve()
                        .body(String.class));
    }

    private void flush() {
        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        meterRegistry.close();
    }

    private static String traceInTempo(String traceId) {
        return http.get()
                .uri(URI.create(grafana.getTempoUrl() + "/api/traces/" + traceId))
                .retrieve()
                .body(String.class);
    }

    private static List<JsonNode> prometheus(String query) {
        var body = http.get()
                .uri(UriComponentsBuilder.fromUriString(grafana.getPrometheusHttpUrl() + "/api/v1/query")
                        .queryParam("query", query)
                        .build()
                        .encode()
                        .toUri())
                .retrieve()
                .body(String.class);

        var results = json.readTree(body).path("data").path("result");
        var found = new java.util.ArrayList<JsonNode>();
        results.forEach(found::add);
        return found;
    }
}
