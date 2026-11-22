package io.eventdriven.core.projections;

import io.eventdriven.core.identifiers.EntityId;
import jakarta.persistence.Embeddable;

@Embeddable
public record CounterId(String value) implements EntityId {}
