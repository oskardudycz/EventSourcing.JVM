package io.eventdriven.testing;

public record RabbitMQConnection(String host, int port, int managementPort, String username, String password) {
    public String managementUrl() {
        return "http://%s:%d".formatted(host, managementPort);
    }
}
