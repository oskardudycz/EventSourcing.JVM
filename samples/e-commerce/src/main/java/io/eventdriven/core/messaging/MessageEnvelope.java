package io.eventdriven.core.messaging;

public record MessageEnvelope<Message>(Message message, MessageMetadata metadata) {}
