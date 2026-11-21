package io.eventdriven.distributedprocesses.ecommerce.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.ApiSpecification;
import io.eventdriven.testing.LgtmApi;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestLgtm;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "messaging.bus=kafka",
            "eventstore.type=kurrentdb",
            "projections.database=postgresql",
            "messaging.retry.initial-interval=100ms"
        })
@AutoConfigureTracing
@AutoConfigureMetrics
public class GrafanaExportTests extends ApiSpecification {
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    record Opened(String cartId) {}

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();
        var lgtm = TestLgtm.shared();

        registry.add("spring.kafka.bootstrap-servers", TestDatabases::kafkaBootstrapServers);
        registry.add("kurrentdb.connectionstring", TestDatabases::kurrentDBConnectionString);
        registry.add("spring.datasource.url", postgreSQL::url);
        registry.add("spring.datasource.username", postgreSQL::username);
        registry.add("spring.datasource.password", postgreSQL::password);
        registry.add(
                "management.opentelemetry.tracing.export.otlp.endpoint", () -> lgtm.getOtlpHttpUrl() + "/v1/traces");
        registry.add("management.tracing.export.otlp.enabled", () -> "true");
        registry.add("management.logging.export.otlp.enabled", () -> "true");
        registry.add("management.otlp.metrics.export.enabled", () -> "true");
        registry.add("management.opentelemetry.tracing.export.schedule-delay", () -> "100ms");
        registry.add("management.opentelemetry.logging.export.otlp.endpoint", () -> lgtm.getOtlpHttpUrl() + "/v1/logs");
        registry.add("management.opentelemetry.logging.export.schedule-delay", () -> "100ms");
        registry.add("management.otlp.metrics.export.url", () -> lgtm.getOtlpHttpUrl() + "/v1/metrics");
        registry.add("management.otlp.metrics.export.step", () -> "1s");
    }

    @Autowired
    private EventStore eventStore;

    @Autowired
    private Tracer tracer;

    private final LgtmApi lgtm = new LgtmApi(TestLgtm.shared());
    private PurchaseRestBuilder.BuilderResult purchase;
    private EventMetadata checkout;

    public GrafanaExportTests() {
        super("customers");
    }

    @BeforeAll
    void completePurchase() {
        var builder = PurchaseRestBuilder.of(restTestClient, port);
        purchase = builder.build(order -> order.withCustomerId(UUID.randomUUID())
                .withProduct(UUID.randomUUID(), 2)
                .checkedOut());
        var checkoutPage = builder.openCheckout(purchase.orderId(), purchase.paymentId());
        builder.payOn(checkoutPage, "4242 4242 4242 4242");

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given(() -> purchase.orderId())
                        .when(GET("/%s/orders".formatted(purchase.customerId()), JsonNode.class))
                        .then(response -> assertThat(((JsonNode) response.getResponseBody())
                                        .get("progress")
                                        .asString())
                                .isEqualTo("Completed")));

        checkout = ((EventStore.ReadResult.Success)
                        eventStore.read(ShoppingCartFacade.mapToStreamId(ShoppingCartId.of(purchase.customerId(), 1))))
                .events()
                .getLast()
                .metadata();
        LogManager.getLogger(GrafanaExportTests.class).error("purchase {} completed", purchase.orderId());
    }

    @Test
    public void tempoFindsTheFirstFlowByCorrelationIdWithItsStepAndAppendSpans() {
        var traceIds = await().atMost(TIMEOUT)
                .until(
                        () -> lgtm.tempoTraceIds("{ span.correlationId = \"%s\" }".formatted(checkout.correlationId())),
                        ids -> !ids.isEmpty());

        assertThat(traceIds).containsExactly(checkout.traceId().replaceFirst("^0+", ""));
        var spans = await().atMost(TIMEOUT)
                .until(
                        () -> lgtm.tempoSpans(checkout.traceId()),
                        found -> found.stream().anyMatch(span -> span.name().equals("streams.append")));
        var appends = spans.stream()
                .filter(span -> span.name().equals("streams.append"))
                .toList();
        assertThat(spans).anyMatch(span -> span.name().startsWith("handle "));
        assertThat(appends)
                .allSatisfy(append -> assertThat(spans)
                        .filteredOn(span -> span.spanId().equals(append.parentSpanId()))
                        .singleElement()
                        .satisfies(parent -> assertThat(parent.name()).matches("handle .*|http .*")));
        assertThat(appends)
                .anySatisfy(append -> assertThat(spans)
                        .filteredOn(span -> span.spanId().equals(append.parentSpanId()))
                        .allMatch(parent -> parent.name().startsWith("handle ")));
    }

    @Test
    public void aFailedAppendMarksItsStreamsAppendSpanAsAnError() {
        var streamId = "ShoppingCart-" + UUID.randomUUID();
        eventStore.append(streamId, new Opened("cart"));

        eventStore.append(streamId, new Opened("cart"));

        var traceIds = await().atMost(TIMEOUT)
                .until(
                        () -> lgtm.tempoTraceIds(
                                "{ name = \"streams.append\" && span.db.kurrentdb.stream = \"%s\" && status = error }"
                                        .formatted(streamId)),
                        ids -> !ids.isEmpty());
        assertThat(lgtm.tempoSpans(traceIds.getFirst()))
                .filteredOn(span -> span.name().equals("streams.append"))
                .anyMatch(span -> span.statusCode().equals("STATUS_CODE_ERROR"));
    }

    @Test
    public void lokiHasTheFailedHandlersLogLinesWithItsTraceId() {
        var streamId = "ShoppingCart-" + UUID.randomUUID();
        var marker = "failing handler " + UUID.randomUUID();
        var failedOnce = new AtomicBoolean();
        eventStore.subscribe("failing-" + UUID.randomUUID(), List.of(Opened.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId) && failedOnce.compareAndSet(false, true)) {
                LogManager.getLogger(GrafanaExportTests.class).error(marker);
                throw new IllegalStateException(marker);
            }
        });

        var testSpan = tracer.nextSpan().name("test").start();
        try (var _ = tracer.withSpan(testSpan)) {
            eventStore.append(streamId, new Opened("cart"));
        } finally {
            testSpan.end();
        }

        var stored = ((EventStore.ReadResult.Success) eventStore.read(streamId))
                .events()
                .getFirst()
                .metadata();
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(lgtm.lokiLines("{service_name=~\".+\"} | trace_id=\"%s\" |= \"%s\""
                                .formatted(stored.traceId(), marker)))
                        .isNotEmpty());
    }

    @Test
    public void dashboardHasThePanelsAndTraceLinks() {
        var dashboard = await().atMost(TIMEOUT)
                .until(() -> lgtm.grafanaDashboard("purchase-flow"), found -> found.has("dashboard"))
                .get("dashboard");

        var panels = dashboard.get("panels");
        assertThat(panels.valueStream().map(panel -> panel.get("title").asString()))
                .containsExactly(
                        "Messages published per channel",
                        "Messages handled per channel",
                        "Messages failed per channel",
                        "Dead letters",
                        "HTTP requests");
        assertThat(panels.valueStream())
                .allSatisfy(
                        panel -> assertThat(panel.get("links").get(0).get("url").asString())
                                .startsWith("/explore")
                                .contains("tempo"));
    }

    @Test
    public void purchaseIsExportedToTempoLokiAndPrometheus() {
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(lgtm.tempoTraceIds(
                                "{ span.correlationId = \"%s\" }".formatted(checkout.correlationId())))
                        .isNotEmpty());
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(lgtm.lokiLines(
                                "{service_name=~\".+\"} |= \"purchase %s completed\"".formatted(purchase.orderId())))
                        .isNotEmpty());
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(List.of(
                                lgtm.prometheusSeries("spring_kafka_listener_milliseconds_count"),
                                lgtm.prometheusSeries("spring_kafka_template_milliseconds_count"),
                                lgtm.prometheusSeries("http_server_requests_milliseconds_count")))
                        .allSatisfy(series -> assertThat(series).isNotEmpty()));
        assertThat(lgtm.grafanaDashboard("purchase-flow").has("dashboard")).isTrue();
    }
}
