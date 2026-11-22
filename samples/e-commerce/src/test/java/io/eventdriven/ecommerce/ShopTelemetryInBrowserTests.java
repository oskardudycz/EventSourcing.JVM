package io.eventdriven.ecommerce;

import static io.eventdriven.testing.grafana.GrafanaApi.flushed;
import static io.eventdriven.testing.grafana.GrafanaApi.traceWith;

import io.eventdriven.ecommerce.shipments.sandbox.WarehouseKeeper;
import io.eventdriven.ecommerce.shop.Customer;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.browser.BrowserE2ESpecification;
import io.eventdriven.testing.eventstore.RecordedEventsConfiguration;
import io.eventdriven.testing.grafana.GrafanaApi;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A customer shops in Chromium on Kafka, KurrentDB and PostgreSQL, where the views catch up after the writes. The
 * pages never show an error while they wait, the order page updates in place, and the purchase reaches Grafana as a
 * trace, metrics and logs.
 */
@DirtiesContext
@AutoConfigureTracing
@AutoConfigureMetrics
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"messaging.bus=kafka", "eventstore.type=kurrentdb", "projections.database=postgresql"})
@Import({InMemorySpansConfiguration.class, RecordedEventsConfiguration.class})
public class ShopTelemetryInBrowserTests extends BrowserE2ESpecification {
    private static final TestDatabases.Grafana grafana = TestDatabases.grafana();
    private static final GrafanaApi grafanaApi = new GrafanaApi(grafana);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();
        var endpoint = grafana.otlpHttpUrl();

        registry.add("spring.kafka.bootstrap-servers", TestDatabases::kafkaBootstrapServers);
        registry.add("kurrentdb.connectionstring", TestDatabases::kurrentDBConnectionString);
        TestDatabases.addProjectionsDatabase(registry, postgreSQL);
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

    private final Customer customer = new Customer(this);
    private final TestStep[] cartWithTwoProducts = {customer.logsIn(), customer.addsToCart(0), customer.addsToCart(1)};
    private final TestStep[] orderAwaitingPayment = scenario(cartWithTwoProducts, customer.checksOut());
    private final TestStep[] paymentPage = scenario(orderAwaitingPayment, customer.paysNow());
    private final TestStep[] paidOrder = scenario(paymentPage, customer.paysWithACardThatWorks());
    private final WarehouseKeeper warehouse = new WarehouseKeeper(this);

    @Test
    public void checkingOutShowsTheOrderAwaitingPaymentOnceTheSagaPlacedIt() {
        given(cartWithTwoProducts)
                .when(customer.checksOut())
                .then(
                        expectPageAt(customer.order()),
                        expectPageShowing("Awaiting payment", "Pay now"),
                        expectNoFailedResponses(),
                        expectTrace(span(
                                "http post /shop/customers/{customerId}/cart/checkout",
                                span("handle ShoppingCartCheckedOut", span("handle PlaceOrder")))),
                        expectCorrelatedTrace());
    }

    @Test
    public void payingReturnsToTheOrderWhichWaitsForTheWarehouseToSendThePackage() {
        given(paymentPage)
                .when(customer.paysWithACardThatWorks())
                .then(expectPageShowing("Processing"), expectPageNotShowing("Pay now"), expectNoFailedResponses());
    }

    @Test
    public void theWarehouseSendingThePackageCompletesTheOrderOnTheCustomersPageInPlace() {
        given(paidOrder)
                .when(warehouse.sendsThePackageOf(customer))
                .then(
                        expectPageShowing("Completed", "Sandbox Express"),
                        expectPageNotShowing("Pay now"),
                        expectUpdatedInPlace(),
                        expectNoFailedResponses());
    }

    @Test
    public void theWarehouseRejectingThePackageCancelsTheOrder() {
        given(paidOrder)
                .when(warehouse.rejectsThePackageOf(customer))
                .then(expectPageShowing("Canceled"), expectNoFailedResponses());
    }

    @Test
    public void theSoldOutProductLeavesTheOrderOutOfStockWithOnlyTheWayToCancelIt() {
        given(customer.logsIn(), customer.addsToCart(4))
                .when(customer.checksOut())
                .then(
                        expectPageShowing("Out of stock", "Cancel order"),
                        expectPageNotShowing("Pay now"),
                        expectNoFailedResponses());
    }

    @Test
    public void theOrdersPageListsTheCompletedOrder() {
        given(scenario(paidOrder, warehouse.sendsThePackageOf(customer), customer.waitsUntilTheOrderIs("Completed")))
                .when(customer.opensOrders())
                .then(
                        expectPageAt("/shop/customers/%s/orders".formatted(customer.customerId)),
                        expectPageShowing("Completed"));
    }

    @Test
    public void aPurchaseReachesGrafanaAsOneTraceAsMetricsAndAsLogs() {
        given(paidOrder)
                .when(warehouse.sendsThePackageOf(customer))
                .then(
                        expectPageShowing("Completed"),
                        flushed(tracerProvider, loggerProvider),
                        grafanaApi.expectTraceInTempo(
                                traceWith(Map.of(
                                        "eventdriven.command.type",
                                        "PlaceOrder",
                                        "eventdriven.stream.name",
                                        "Order-" + customer.orderTail)),
                                span(
                                        "http post /shop/customers/{customerId}/cart/checkout",
                                        span(
                                                "handle ShoppingCartCheckedOut",
                                                span("handle PlaceOrder", span("command.handle"))))),
                        grafanaApi.expectCommandsHandled("PlaceOrder"),
                        grafanaApi.expectEventsAppended("OrderCompleted"),
                        grafanaApi.expectLogsOf("e-commerce"),
                        grafanaApi.expectMetricInPrometheus(
                                "increase(ecommerce_order_lead_time_milliseconds_count{ecommerce_order_stage=\"completed\"}[5m])"),
                        screenshotOf("purchases", "13-grafana-purchases"),
                        screenshotOf("eventdriven-internals", "14-grafana-commands-and-event-store"),
                        screenshotOf("purchase-flow", "15-grafana-purchase-flow"),
                        expectPage(current -> grafanaApi.screenshotOfRow(
                                current,
                                "purchases",
                                "Business",
                                "Order lead time",
                                SCREENSHOTS.resolve("18-grafana-business.png"))),
                        expectPage(current -> grafanaApi.screenshotOfAlertRules(
                                current,
                                "E-Commerce",
                                "Capture failed",
                                SCREENSHOTS.resolve("19-grafana-alert-rules.png"))));
    }

    @Override
    protected boolean isAbout(Object message) {
        return message.toString().contains(customer.customerId.toString());
    }

    private Expectation screenshotOf(String dashboardUid, String name) {
        return expectPage(
                current -> grafanaApi.screenshotOf(current, dashboardUid, SCREENSHOTS.resolve(name + ".png")));
    }
}
