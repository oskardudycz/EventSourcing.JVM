package io.eventdriven.core.events;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventMetadata(
        @JsonProperty("$correlationId") String correlationId,
        @JsonProperty("$causationId") String causationId) {}
