package io.eventdriven.distributedprocesses.ecommerce.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import java.util.UUID;

public record OrderId(@JsonValue String value) implements EntityId {
  private static final String type = "order";

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public OrderId {
    Urns.requireType(value, type);
  }

  public static OrderId of(UUID id) {
    return new OrderId(Urns.of("ecommerce", type, id));
  }

  public static OrderId derivedFrom(String sourceUrn) {
    return new OrderId(Urns.derive(sourceUrn, type));
  }

  @Override
  public String toString() {
    return value;
  }
}
