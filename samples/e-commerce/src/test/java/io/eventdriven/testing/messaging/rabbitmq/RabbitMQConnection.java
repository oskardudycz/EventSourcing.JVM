package io.eventdriven.testing.messaging.rabbitmq;

public record RabbitMQConnection(String host, int port, int managementPort, String username, String password) {
    public String managementUrl() {
        return "http://%s:%d".formatted(host, managementPort);
    }
}
