package io.eventdriven.distributedprocesses.ecommerce.payments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import java.util.UUID;

public record PaymentId(@JsonValue String value) implements EntityId {
  private static final String type = "payment";

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public PaymentId {
    Urns.requireType(value, type);
  }

  public static PaymentId of(UUID id) {
    return new PaymentId(Urns.of("ecommerce", type, id));
  }

  public static PaymentId derivedFrom(String sourceUrn) {
    return new PaymentId(Urns.derive(sourceUrn, type));
  }

  @Override
  public String toString() {
    return value;
  }
}
