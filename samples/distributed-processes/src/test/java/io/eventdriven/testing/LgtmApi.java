package io.eventdriven.testing;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.grafana.LgtmStackContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class LgtmApi {
    public record Span(String name, String spanId, String parentSpanId, String statusCode) {}

    private static final JsonMapper mapper = new JsonMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final LgtmStackContainer lgtm;

    public LgtmApi(LgtmStackContainer lgtm) {
        this.lgtm = lgtm;
    }

    public int statusOf(String url) {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                    .statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    public List<String> tempoTraceIds(String traceQl) {
        var now = Instant.now().getEpochSecond();
        var search = get(lgtm.getTempoUrl()
                + "/api/search?limit=50&start=%d&end=%d&q=%s".formatted(now - 3600, now + 60, encode(traceQl)));

        var ids = new ArrayList<String>();
        search.path("traces").forEach(trace -> ids.add(trace.get("traceID").asString()));

        return ids;
    }

    public List<Span> tempoSpans(String traceId) {
        var trace = get(lgtm.getTempoUrl() + "/api/traces/" + traceId);

        var spans = new ArrayList<Span>();
        trace.path("batches")
                .forEach(batch -> batch.path("scopeSpans")
                        .forEach(scope -> scope.path("spans")
                                .forEach(span -> spans.add(new Span(
                                        span.get("name").asString(),
                                        span.get("spanId").asString(),
                                        span.path("parentSpanId").asString(""),
                                        span.path("status").path("code").asString(""))))));

        return spans;
    }

    public List<String> lokiLines(String logQl) {
        var now = Instant.now().getEpochSecond();
        var result = get(lgtm.getLokiUrl()
                + "/loki/api/v1/query_range?limit=100&start=%d000000000&end=%d000000000&query=%s"
                        .formatted(now - 3600, now + 60, encode(logQl)));

        var lines = new ArrayList<String>();
        result.path("data")
                .path("result")
                .forEach(stream -> stream.path("values")
                        .forEach(value -> lines.add(value.get(1).asString())));

        return lines;
    }

    public List<JsonNode> prometheusSeries(String promQl) {
        var result = get(lgtm.getPrometheusHttpUrl() + "/api/v1/query?query=" + encode(promQl));

        var series = new ArrayList<JsonNode>();
        result.path("data").path("result").forEach(series::add);

        return series;
    }

    public JsonNode grafanaDashboard(String uid) {
        return get(lgtm.getGrafanaHttpUrl() + "/api/dashboards/uid/" + uid);
    }

    private JsonNode get(String url) {
        try {
            var response =
                    http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200 && !response.body().isBlank()
                    ? mapper.readTree(response.body())
                    : mapper.createObjectNode();
        } catch (Exception e) {
            throw new IllegalStateException("GET %s failed".formatted(url), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
