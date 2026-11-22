package io.eventdriven.core.messaging;

public record MessageQueue(String name, String channel, String routingPattern) {}
