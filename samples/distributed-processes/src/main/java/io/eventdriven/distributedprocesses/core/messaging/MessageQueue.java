package io.eventdriven.distributedprocesses.core.messaging;

public record MessageQueue(String name, String channel, String routingPattern) {}
