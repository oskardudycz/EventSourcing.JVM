package io.eventdriven.testing.tracing;

import io.eventdriven.testing.TestDatabases;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads what a test sent to Grafana: metrics from Prometheus, traces from Tempo and logs from Loki. */
public final class GrafanaApi {
    private static final JsonMapper json = JsonMapper.builder().build();
    private static final RestClient http = RestClient.create();

    private final TestDatabases.Grafana grafana;

    public GrafanaApi(TestDatabases.Grafana grafana) {
        this.grafana = grafana;
    }

    public JsonNode get(String path) {
        return json.readTree(http.get()
                .uri(URI.create(grafana.url() + path))
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .body(String.class));
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

    public String trace(String traceId) {
        return get("/api/datasources/proxy/uid/tempo/api/traces/" + traceId).toString();
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
}
