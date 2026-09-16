package io.eventdriven.distributedprocesses.ecommerce.shipments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import java.util.UUID;

public record ShipmentId(@JsonValue String value) implements EntityId {
  private static final String type = "shipment";

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public ShipmentId {
    Urns.requireType(value, type);
  }

  public static ShipmentId of(UUID id) {
    return new ShipmentId(Urns.of("ecommerce", type, id));
  }

  public static ShipmentId derivedFrom(String sourceUrn) {
    return new ShipmentId(Urns.derive(sourceUrn, type));
  }

  @Override
  public String toString() {
    return value;
  }
}
