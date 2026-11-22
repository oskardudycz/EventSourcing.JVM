package io.eventdriven.ecommerce;

import static io.eventdriven.ecommerce.orders.OrderRequests.payNow;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.addProductItem;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.checkOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpStatus.ACCEPTED;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.api.ApiE2ESpecification;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@AutoConfigureMetrics
@ActiveProfiles("in-memory")
@Import({OutsideWorld.class, InMemorySpansConfiguration.class})
public class OtlpExportTests extends ApiE2ESpecification {
    private static final Duration ingestionTimeout = Duration.ofSeconds(60);
    private static final TestDatabases.Grafana grafana = TestDatabases.grafana();
    private static final Duration paymentValidity = Duration.ofHours(24);
    private static final JsonMapper json = JsonMapper.builder().build();
    private static final RestClient http = RestClient.create();

    @DynamicPropertySource
    static void exportToGrafana(DynamicPropertyRegistry registry) {
        var endpoint = grafana.otlpHttpUrl();

        registry.add("management.tracing.export.otlp.enabled", () -> "true");
        registry.add("management.logging.export.otlp.enabled", () -> "true");
        registry.add("management.otlp.metrics.export.enabled", () -> "true");
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> endpoint + "/v1/traces");
        registry.add("management.opentelemetry.logging.export.otlp.endpoint", () -> endpoint + "/v1/logs");
        registry.add("management.otlp.metrics.export.url", () -> endpoint + "/v1/metrics");
        registry.add("management.otlp.metrics.export.step", () -> "100ms");
    }

    @Autowired
    private SdkTracerProvider tracerProvider;

    @Autowired
    private SdkLoggerProvider loggerProvider;

    @Autowired
    private Time time;

    @Test
    public void theDashboardsShowAPurchaseWhosePaymentTimedOut() {
        var customerId = UUID.randomUUID();
        var orderId = Order.Id.derivedFrom(ShoppingCart.Id.of(customerId, 1).value());
        var paymentId = Payment.Id.derivedFrom(orderId.value());

        given(
                        addProductItem(customerId, UUID.randomUUID(), 2),
                        checkOut(customerId, weakETag(0)),
                        payNow(customerId, orderId, paymentId))
                .when(time.passes(paymentValidity))
                .then(outcome -> {});
        flush();

        assertThat(dashboardTitles()).contains("Purchase flow", "Commands and event store", "Purchases");
        for (var query : dashboardQueries())
            assertThat(prometheusStatus(query))
                    .as("Status of the query %s", query)
                    .isEqualTo("success");
        for (var panel : List.of(
                "Commands handled",
                "Events appended",
                "Commands by type and status",
                "Events appended by type",
                "Reads and appends by status",
                "HTTP requests",
                "Orders placed",
                "Orders by outcome",
                "Payments",
                "Shipments",
                "Timeouts"))
            await().atMost(ingestionTimeout)
                    .untilAsserted(() -> assertThat(dataOf(panel))
                            .as("Data of the panel %s", panel)
                            .isNotEmpty());
    }

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
                                prometheus(
                                        "eventdriven_command_handle_milliseconds_count{eventdriven_command_type=\"PlaceOrder\"}"))
                        .isNotEmpty());
    }

    private void flush() {
        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);
    }

    private static List<String> dashboardTitles() {
        await().atMost(ingestionTimeout)
                .until(() -> grafanaGet("/api/search?type=dash-db").size() >= 3);

        var titles = new ArrayList<String>();
        grafanaGet("/api/search?type=dash-db")
                .forEach(dashboard -> titles.add(dashboard.path("title").asString()));
        return titles;
    }

    private static List<JsonNode> panels() {
        var panels = new ArrayList<JsonNode>();
        grafanaGet("/api/search?type=dash-db")
                .forEach(dashboard -> grafanaGet(
                                "/api/dashboards/uid/" + dashboard.path("uid").asString())
                        .path("dashboard")
                        .path("panels")
                        .forEach(panels::add));
        return panels;
    }

    private static List<String> dashboardQueries() {
        var queries = new ArrayList<String>();
        panels().forEach(panel -> panel.path("targets")
                .forEach(target ->
                        queries.add(withConcreteRanges(target.path("expr").asString()))));
        return queries;
    }

    private static List<JsonNode> dataOf(String panelTitle) {
        var data = new ArrayList<JsonNode>();
        panels().stream()
                .filter(panel -> panel.path("title").asString().equals(panelTitle))
                .forEach(panel -> panel.path("targets")
                        .forEach(target -> prometheus(
                                        withConcreteRanges(target.path("expr").asString()))
                                .stream()
                                .filter(result -> result.path("value").path(1).asDouble() > 0)
                                .forEach(data::add)));
        return data;
    }

    private static String withConcreteRanges(String expression) {
        return expression
                .replace("$__rate_interval", "5m")
                .replace("$__interval", "5m")
                .replace("$__range", "1h");
    }

    private static JsonNode grafanaGet(String path) {
        return json.readTree(
                http.get().uri(URI.create(grafana.url() + path)).retrieve().body(String.class));
    }

    private static String prometheusStatus(String query) {
        return json.readTree(prometheusResponse(query)).path("status").asString();
    }

    private static String traceInTempo(String traceId) {
        return http.get()
                .uri(URI.create(grafana.url() + "/api/datasources/proxy/uid/tempo/api/traces/" + traceId))
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {})
                .body(String.class);
    }

    private static List<JsonNode> prometheus(String query) {
        var found = new ArrayList<JsonNode>();
        json.readTree(prometheusResponse(query)).path("data").path("result").forEach(found::add);
        return found;
    }

    private static String prometheusResponse(String query) {
        return http.get()
                .uri(UriComponentsBuilder.fromUriString(
                                grafana.url() + "/api/datasources/proxy/uid/prometheus/api/v1/query")
                        .queryParam("query", query)
                        .build()
                        .encode()
                        .toUri())
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .body(String.class);
    }
}
