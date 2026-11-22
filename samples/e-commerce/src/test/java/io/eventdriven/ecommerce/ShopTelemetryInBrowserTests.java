package io.eventdriven.ecommerce;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.tracing.GrafanaApi;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A customer shops in Chromium on Kafka, KurrentDB and PostgreSQL, where the views catch up after the writes. The test
 * checks that the pages never show an error while they wait, that the order page updates in place, and that the
 * purchase reaches Grafana as a trace, metrics and logs.
 */
@DirtiesContext
@AutoConfigureTracing
@AutoConfigureMetrics
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"messaging.bus=kafka", "eventstore.type=kurrentdb", "projections.database=postgresql"})
public class ShopTelemetryInBrowserTests {
    private static final Path screenshots = Path.of(System.getProperty("screenshots.dir", "build/screenshots"));
    private static final TestDatabases.Grafana grafana = TestDatabases.grafana();
    private static final GrafanaApi grafanaApi = new GrafanaApi(grafana);
    private static final Duration ingestion = Duration.ofSeconds(60);

    private static Playwright playwright;
    private static Browser browser;

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

    @LocalServerPort
    private int port;

    @Autowired
    private SdkTracerProvider tracerProvider;

    @Autowired
    private SdkLoggerProvider loggerProvider;

    private Page page;
    private final List<String> browserErrors = new ArrayList<>();
    private final List<String> failedResponses = new ArrayList<>();

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        page = browser.newContext(new Browser.NewContextOptions().setViewportSize(1400, 1000))
                .newPage();
        page.onConsoleMessage(message -> {
            if (message.type().equals("error")) browserErrors.add(message.text());
        });
        page.onPageError(browserErrors::add);
        page.onResponse(response -> {
            if (response.status() >= 400 && response.url().startsWith(url("")))
                failedResponses.add(response.status() + " " + response.url());
        });
    }

    @AfterEach
    void closePage() {
        page.context().close();
        assertThat(browserErrors).as("browser errors").isEmpty();
    }

    @Test
    public void aPurchaseWaitsSmoothlyForTheViewsAndReachesGrafana() {
        page.navigate(url("/shop"));
        page.getByLabel("Your name").fill("Marty McFly " + UUID.randomUUID());
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in"))
                .click();
        var products = page.url();
        addToCart(0);
        page.navigate(products);
        addToCart(1);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Check out"))
                .click();
        assertThat(page.getByText("Awaiting payment")).isVisible();
        var orderTail = page.url().replaceAll(".*/orders/", "");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pay now"))
                .click();
        page.locator("#cardNumber").fill("4242 4242 4242 4242");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Pay \\$")))
                .click();
        page.waitForURL(Pattern.compile(".*/orders/" + Pattern.quote(orderTail) + ".*"));
        page.evaluate("window.stayedOnPage = true");
        assertThat(page.getByText("Completed")).isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(30_000));
        assertThat(page.evaluate("window.stayedOnPage === true"))
                .as("the order page updated in place, without a reload")
                .isEqualTo(true);
        assertThat(failedResponses)
                .as("failed responses while the views caught up")
                .isEmpty();

        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        var orderStream = "Order-" + orderTail;
        await().atMost(ingestion)
                .untilAsserted(() -> assertThat(
                                grafanaApi.traceIds("{ span.eventdriven.stream.name = \"%s\" }".formatted(orderStream)))
                        .isNotEmpty());
        var checkoutTraceId = grafanaApi
                .traceIds("{ span.eventdriven.command.type = \"PlaceOrder\" && span.eventdriven.stream.name = \"%s\" }"
                        .formatted(orderStream))
                .getFirst();
        await().atMost(ingestion)
                .untilAsserted(() -> assertThat(grafanaApi.trace(checkoutTraceId))
                        .contains(
                                "http post /shop/customers/{customerId}/cart/checkout",
                                "handle ShoppingCartCheckedOut",
                                "handle PlaceOrder",
                                "command.handle"));
        await().atMost(ingestion)
                .untilAsserted(() -> assertThat(
                                grafanaApi.sum(
                                        "sum(eventdriven_event_appending_count_total{service_name=\"e-commerce\",eventdriven_event_type=\"OrderCompleted\"})"))
                        .isPositive());
        await().atMost(ingestion)
                .untilAsserted(() -> assertThat(grafanaApi.logLines("{service_name=\"e-commerce\"}"))
                        .isNotEmpty());

        screenshotDashboard("purchases", "13-grafana-purchases");
        screenshotDashboard("eventdriven-internals", "14-grafana-commands-and-event-store");
        screenshotDashboard("purchase-flow", "15-grafana-purchase-flow");
    }

    private void addToCart(int productIndex) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add to cart"))
                .nth(productIndex)
                .click();
        page.waitForURL(Pattern.compile(".*/cart$"));
    }

    private void screenshotDashboard(String uid, String name) {
        page.setViewportSize(1400, 1700);
        page.navigate(grafana.url() + "/d/" + uid + "?orgId=1&from=now-15m&to=now&kiosk");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("canvas").first().waitFor();
        page.waitForFunction("document.querySelectorAll('[aria-label*=\"Loading\"], .panel-loading').length === 0");
        page.screenshot(new Page.ScreenshotOptions().setPath(screenshots.resolve(name + ".png")));
    }

    private String url(String path) {
        return "http://localhost:%d%s".formatted(port, path);
    }
}
