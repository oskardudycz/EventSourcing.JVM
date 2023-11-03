package io.eventdriven.distributedprocesses.core.commands;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CommandMetadata(
    @JsonProperty("$correlationId") String correlationId,
    @JsonProperty("$causationId") String causationId) {}
