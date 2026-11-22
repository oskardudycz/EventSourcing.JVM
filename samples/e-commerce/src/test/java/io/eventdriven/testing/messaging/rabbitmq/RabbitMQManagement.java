package io.eventdriven.testing.messaging.rabbitmq;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class RabbitMQManagement {
    public record Binding(String source, String destination, String routingKey) {}

    private static final String VHOST = "/";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient client;

    public RabbitMQManagement(RabbitMQConnection connection) {
        this.client = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .build()))
                .baseUrl(connection.managementUrl() + "/api")
                .defaultHeaders(headers -> headers.setBasicAuth(connection.username(), connection.password()))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public List<String> queueNames() {
        return namesOf("/queues");
    }

    public List<String> exchangeNames() {
        return namesOf("/exchanges");
    }

    public Map<String, Object> exchange(String name) {
        return client.get()
                .uri("/exchanges/{vhost}/{name}", VHOST, name)
                .retrieve()
                .body(MAP);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> queue(String name) {
        return client.get()
                .uri("/queues/{vhost}/{name}", VHOST, name)
                .retrieve()
                .body(MAP);
    }

    public List<Binding> bindingsOf(String queue) {
        return client.get().uri("/queues/{vhost}/{queue}/bindings", VHOST, queue).retrieve().body(LIST_OF_MAPS).stream()
                .filter(binding -> !"".equals(binding.get("source")))
                .map(binding ->
                        new Binding((String) binding.get("source"), (String) binding.get("destination"), (String)
                                binding.get("routing_key")))
                .toList();
    }

    public void deleteContaining(String text) {
        for (var queue : queueNames()) if (queue.contains(text)) delete("/queues/{vhost}/{name}", queue);

        for (var exchange : exchangeNames()) if (exchange.contains(text)) delete("/exchanges/{vhost}/{name}", exchange);
    }

    private void delete(String path, String name) {
        client.delete()
                .uri(path, VHOST, name)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {})
                .toBodilessEntity();
    }

    private List<String> namesOf(String path) {
        return client.get().uri(path).retrieve().body(LIST_OF_MAPS).stream()
                .map(item -> (String) item.get("name"))
                .toList();
    }
}
