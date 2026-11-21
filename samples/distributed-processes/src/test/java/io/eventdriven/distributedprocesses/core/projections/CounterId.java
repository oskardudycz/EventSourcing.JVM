package io.eventdriven.distributedprocesses.core.projections;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import jakarta.persistence.Embeddable;

@Embeddable
record CounterId(String value) implements EntityId {}
