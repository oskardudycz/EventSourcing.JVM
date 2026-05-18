package io.eventdriven.eventsversioning.snapshottesting;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.eventdriven.eventsversioning.serialization.Serializer;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

public class EventsSnapshotTests {
  private static final UUID FIXED_CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final LocalDateTime FIXED_DATE = LocalDateTime.of(2024, 1, 1, 12, 0, 0);

  record ShoppingCartConfirmed(
    UUID shoppingCartId,
    String clientId,
    LocalDateTime confirmedAt
  ) {}

  @Test
  public void shoppingCartConfirmed_WithCompleteData_IsCompatible() throws JsonProcessingException {
    var event = new ShoppingCartConfirmed(FIXED_CART_ID, "anonymised", FIXED_DATE);
    Approvals.verify(Serializer.mapper.writeValueAsString(event));
  }

  @Test
  public void shoppingCartConfirmed_WithOnlyRequiredData_IsCompatible() throws JsonProcessingException {
    var event = new ShoppingCartConfirmed(FIXED_CART_ID, null, FIXED_DATE);
    Approvals.verify(Serializer.mapper.writeValueAsString(event));
  }
}