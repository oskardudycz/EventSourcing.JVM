package io.eventdriven.ecommerce;

import static io.eventdriven.ecommerce.orders.OrderRequests.payNow;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.addProductItem;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartRequests.checkOut;
import static io.eventdriven.testing.grafana.GrafanaApi.flushed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.ACCEPTED;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.api.ApiE2ESpecification;
import io.eventdriven.testing.grafana.GrafanaApi;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** A purchase sent to a real Grafana LGTM: its trace, metrics and logs arrive, and the dashboards show them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTracing
@AutoConfigureMetrics
@ActiveProfiles("in-memory")
@Import({OutsideWorld.class, InMemorySpansConfiguration.class})
public class OtlpExportTests extends ApiE2ESpecification {
    private static final TestDatabases.Grafana grafana = TestDatabases.grafana();
    private static final GrafanaApi grafanaApi = new GrafanaApi(grafana);
    private static final Duration paymentValidity = Duration.ofHours(24);

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

    private final UUID customerId = UUID.randomUUID();
    private final Order.Id orderId =
            Order.Id.derivedFrom(ShoppingCart.Id.of(customerId, 1).value());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(orderId.value());

    @Test
    public void aCheckoutReachesGrafanaAsOneTraceAsMetricsAndAsLogs() {
        given(addProductItem(customerId, UUID.randomUUID(), 2))
                .when(checkOut(customerId, weakETag(0)))
                .then(
                        expectResponse(ACCEPTED),
                        flushed(tracerProvider, loggerProvider),
                        grafanaApi.expectTraceInTempo(span(
                                "http post /customers/{customerId}/shopping-carts/current/checkout",
                                span(
                                        "handle ShoppingCartCheckedOut",
                                        span(
                                                "handle PlaceOrder",
                                                span(
                                                        "command.handle",
                                                        Map.of("eventdriven.command.type", "PlaceOrder")))))),
                        grafanaApi.expectCommandsHandled("PlaceOrder"),
                        grafanaApi.expectEventsAppended("OrderPlaced"),
                        grafanaApi.expectLogsOf("e-commerce"));
    }

    @Test
    public void theDashboardsShowAPurchaseWhosePaymentTimedOut() {
        given(
                        addProductItem(customerId, UUID.randomUUID(), 2),
                        checkOut(customerId, weakETag(0)),
                        payNow(customerId, orderId, paymentId))
                .when(time.passes(paymentValidity))
                .then(
                        flushed(tracerProvider, loggerProvider),
                        outcome -> assertThat(grafanaApi.dashboardTitles())
                                .contains("Purchase flow", "Commands and event store", "Purchases"),
                        grafanaApi.expectValidDashboardQueries(),
                        grafanaApi.expectDashboardShowing(
                                "eventdriven-internals",
                                "Commands handled",
                                "Events appended",
                                "Commands by type and status",
                                "Events appended by type",
                                "Reads and appends by status",
                                "Subscription lag"),
                        grafanaApi.expectDashboardShowing(
                                "purchases",
                                "Orders placed",
                                "Orders by outcome",
                                "Payments",
                                "Shipments",
                                "Timeouts",
                                "Subscription lag"));
    }
}
