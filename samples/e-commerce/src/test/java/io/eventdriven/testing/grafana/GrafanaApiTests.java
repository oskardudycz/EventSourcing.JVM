package io.eventdriven.testing.grafana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.eventdriven.testing.TestDatabases;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class GrafanaApiTests {
    private final ScriptedGrafana grafana = new ScriptedGrafana();
    private final GrafanaApi grafanaApi = new GrafanaApi(new TestDatabases.Grafana(grafana.url(), grafana.url()));

    @AfterEach
    void stopGrafana() {
        grafana.stop();
    }

    @Test
    public void aDashboardQueryIsAskedAgainWhileGrafanaAnswersWithAServerError() {
        grafana.answersTheQuery(
                new ScriptedGrafana.Answer(502, ""),
                new ScriptedGrafana.Answer(200, "{\"status\":\"success\",\"data\":{\"result\":[]}}"));

        grafanaApi.expectValidDashboardQueries().verify(null);

        assertThat(grafana.queriesAsked()).isEqualTo(2);
    }

    @Test
    public void anInvalidDashboardQueryFailsAtOnceWithPrometheusAnswer() {
        grafana.answersTheQuery(new ScriptedGrafana.Answer(
                400, "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"parse error\"}"));

        assertThatThrownBy(() -> grafanaApi.expectValidDashboardQueries().verify(null))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("parse error");
        assertThat(grafana.queriesAsked()).isEqualTo(1);
    }

    static class ScriptedGrafana {
        record Answer(int status, String body) {}

        private final HttpServer server;
        private final Queue<Answer> answers = new ArrayDeque<>();
        private final AtomicInteger queriesAsked = new AtomicInteger();
        private Answer lastAnswer;

        ScriptedGrafana() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/api/search", exchange -> respond(exchange, 200, "[{\"uid\":\"shop\"}]"));
            server.createContext(
                    "/api/dashboards/uid/shop",
                    exchange ->
                            respond(exchange, 200, "{\"dashboard\":{\"panels\":[{\"targets\":[{\"expr\":\"up\"}]}]}}"));
            server.createContext("/api/datasources/proxy/uid/prometheus/api/v1/query", exchange -> {
                queriesAsked.incrementAndGet();
                var answer = nextAnswer();
                respond(exchange, answer.status(), answer.body());
            });
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void answersTheQuery(Answer... scripted) {
            answers.addAll(List.of(scripted));
        }

        int queriesAsked() {
            return queriesAsked.get();
        }

        void stop() {
            server.stop(0);
        }

        private synchronized Answer nextAnswer() {
            if (!answers.isEmpty()) lastAnswer = answers.poll();
            return lastAnswer;
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
