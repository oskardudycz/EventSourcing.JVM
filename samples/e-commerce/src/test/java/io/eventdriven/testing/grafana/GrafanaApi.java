package io.eventdriven.testing.grafana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.api.ApiE2ESpecification.Expectation;
import io.eventdriven.testing.api.ApiE2ESpecification.ExpectedSpan;
import io.eventdriven.testing.api.SpanTree;
import io.eventdriven.testing.api.SpanTree.RecordedSpan;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads what a test sent to Grafana: metrics from Prometheus, traces from Tempo and logs from Loki, all through
 * Grafana's data source proxy. Its expectations wait until Grafana has the data, because the export is asynchronous.
 */
public final class GrafanaApi {
    private static final Duration INGESTION = Duration.ofSeconds(60);
    private static final JsonMapper json = JsonMapper.builder().build();
    private static final RestClient http = RestClient.create();

    private final TestDatabases.Grafana grafana;

    public GrafanaApi(TestDatabases.Grafana grafana) {
        this.grafana = grafana;
    }

    public JsonNode get(String path) {
        var body = http.get()
                .uri(URI.create(grafana.url() + path))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .body(String.class);
        return body == null ? json.missingNode() : json.readTree(body);
    }

    public List<JsonNode> prometheus(String query) {
        var found = new ArrayList<JsonNode>();
        prometheusResponse(query).path("data").path("result").forEach(found::add);
        return found;
    }

    public JsonNode prometheusResponse(String query) {
        return get(proxied("prometheus", "/api/v1/query", "query", query));
    }

    public double sum(String query) {
        return prometheus(query).stream()
                .mapToDouble(result -> result.path("value").path(1).asDouble())
                .sum();
    }

    public List<String> traceIds(String traceQl) {
        var ids = new ArrayList<String>();
        get(proxied("tempo", "/api/search", "q", traceQl))
                .path("traces")
                .forEach(trace -> ids.add(trace.path("traceID").asString()));
        return ids;
    }

    public List<String> logLines(String logQl) {
        var lines = new ArrayList<String>();
        get(proxied("loki", "/loki/api/v1/query_range", "query", logQl))
                .path("data")
                .path("result")
                .forEach(stream -> stream.path("values")
                        .forEach(value -> lines.add(value.path(1).asString())));
        return lines;
    }

    private static String proxied(String datasource, String path, String parameter, String value) {
        return UriComponentsBuilder.fromPath("/api/datasources/proxy/uid/" + datasource + path)
                .queryParam(parameter, value)
                .build()
                .encode()
                .toUriString();
    }

    public List<RecordedSpan> spansOf(String traceId) {
        var spans = new ArrayList<RecordedSpan>();
        get("/api/datasources/proxy/uid/tempo/api/traces/" + traceId)
                .path("batches")
                .forEach(batch -> batch.path("scopeSpans")
                        .forEach(scope -> scope.path("spans").forEach(span -> {
                            var attributes = new TreeMap<String, String>();
                            span.path("attributes")
                                    .forEach(attribute -> attributes.put(
                                            attribute.path("key").asString(),
                                            attribute
                                                    .path("value")
                                                    .iterator()
                                                    .next()
                                                    .asString()));
                            spans.add(new RecordedSpan(
                                    traceId,
                                    span.path("spanId").asString(),
                                    span.path("parentSpanId").asString(),
                                    span.path("name").asString(),
                                    attributes));
                        })));
        return spans;
    }

    /** The traces that the scenario started reached Tempo with these spans, as {@code expectTrace} checks them. */
    public Expectation expectTraceInTempo(ExpectedSpan root) {
        return outcome -> {
            var traceIds = outcome.spans().get().stream()
                    .map(SpanData::getTraceId)
                    .distinct()
                    .toList();
            awaitTrace(root, () -> traceIds);
        };
    }

    /** A trace that the TraceQL query finds reached Tempo with these spans. */
    public Expectation expectTraceInTempo(String traceQl, ExpectedSpan root) {
        return outcome -> awaitTrace(root, () -> traceIds(traceQl));
    }

    /** A TraceQL query for the traces with a span that has all these attributes. */
    public static String traceWith(Map<String, String> spanAttributes) {
        return spanAttributes.entrySet().stream()
                .map(attribute -> "span.%s = \"%s\"".formatted(attribute.getKey(), attribute.getValue()))
                .collect(Collectors.joining(" && ", "{ ", " }"));
    }

    public Expectation expectEventsAppended(String eventType) {
        return expectMetricInPrometheus(
                "sum(eventdriven_event_appending_count_total{eventdriven_event_type=\"%s\"})".formatted(eventType));
    }

    public Expectation expectCommandsHandled(String commandType) {
        return expectMetricInPrometheus("sum(eventdriven_command_handling_count_total{eventdriven_command_type=\"%s\"})"
                .formatted(commandType));
    }

    public Expectation expectLogsOf(String serviceName) {
        return expectLogsInLoki("{service_name=\"%s\"}".formatted(serviceName));
    }

    /** Sends the spans and logs the application still buffers, so Grafana gets them before the checks that follow. */
    public static Expectation flushed(SdkTracerProvider tracerProvider, SdkLoggerProvider loggerProvider) {
        return outcome -> {
            tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
            loggerProvider.forceFlush().join(10, TimeUnit.SECONDS);
        };
    }

    /** Saves a screenshot of the dashboard once its charts are drawn. */
    public void screenshotOf(Page page, String dashboardUid, Path file) {
        page.setViewportSize(1400, 1700);
        page.navigate(grafana.url() + "/d/" + dashboardUid + "?orgId=1&from=now-15m&to=now&kiosk");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("canvas").first().waitFor();
        page.screenshot(new Page.ScreenshotOptions().setPath(file));
    }

    /** The PromQL query answers a value above zero. */
    public Expectation expectMetricInPrometheus(String query) {
        return outcome -> await().atMost(INGESTION)
                .untilAsserted(
                        () -> assertThat(sum(query)).as("Value of %s", query).isPositive());
    }

    /** The LogQL query finds log lines. */
    public Expectation expectLogsInLoki(String logQl) {
        return outcome -> await().atMost(INGESTION)
                .untilAsserted(() ->
                        assertThat(logLines(logQl)).as("Log lines of %s", logQl).isNotEmpty());
    }

    /** Every query of every dashboard is valid PromQL. */
    public Expectation expectValidDashboardQueries() {
        return outcome -> {
            for (var query : dashboardQueries())
                assertThat(prometheusResponse(query).path("status").asString())
                        .as("Status of the query %s", query)
                        .isEqualTo("success");
        };
    }

    /** The dashboard has these panels, and each of them shows data. */
    public Expectation expectDashboardShowing(String dashboardUid, String... panelTitles) {
        return outcome -> {
            for (var title : panelTitles)
                await().atMost(INGESTION)
                        .untilAsserted(() -> assertThat(dataOf(dashboardUid, title))
                                .as("Data of the panel %s on %s", title, dashboardUid)
                                .isNotEmpty());
        };
    }

    public List<String> dashboardTitles() {
        await().atMost(INGESTION).until(() -> get("/api/search?type=dash-db").size() >= 1);

        var titles = new ArrayList<String>();
        get("/api/search?type=dash-db")
                .forEach(dashboard -> titles.add(dashboard.path("title").asString()));
        return titles;
    }

    private void awaitTrace(ExpectedSpan root, Supplier<List<String>> traceIds) {
        await().atMost(INGESTION).untilAsserted(() -> {
            var spans = traceIds.get().stream()
                    .flatMap(traceId -> spansOf(traceId).stream())
                    .toList();
            var mismatch = SpanTree.mismatchOf(root, spans);

            assertThat(mismatch)
                    .as("%s%nSpans in Tempo:%n%s", mismatch.orElse(""), SpanTree.describe(spans))
                    .isEmpty();
        });
    }

    private List<JsonNode> panels(String dashboardUid) {
        var panels = new ArrayList<JsonNode>();
        get("/api/dashboards/uid/" + dashboardUid)
                .path("dashboard")
                .path("panels")
                .forEach(panels::add);
        return panels;
    }

    private List<String> dashboardQueries() {
        var queries = new ArrayList<String>();
        get("/api/search?type=dash-db")
                .forEach(dashboard -> panels(dashboard.path("uid").asString())
                        .forEach(panel -> panel.path("targets")
                                .forEach(target -> queries.add(
                                        withConcreteRanges(target.path("expr").asString())))));
        return queries;
    }

    private List<JsonNode> dataOf(String dashboardUid, String panelTitle) {
        var data = new ArrayList<JsonNode>();
        panels(dashboardUid).stream()
                .filter(panel -> panel.path("title").asString().equals(panelTitle))
                .forEach(panel -> panel.path("targets")
                        .forEach(target -> prometheus(
                                        withConcreteRanges(target.path("expr").asString()))
                                .stream()
                                .filter(result ->
                                        isPositive(result.path("value").path(1).asString()))
                                .forEach(data::add)));
        return data;
    }

    private static boolean isPositive(String value) {
        var number = Double.parseDouble(value);
        return Double.isFinite(number) && number > 0;
    }

    private static String withConcreteRanges(String expression) {
        return expression
                .replace("$__rate_interval", "5m")
                .replace("$__interval", "5m")
                .replace("$__range", "1h");
    }
}
