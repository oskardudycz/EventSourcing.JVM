package io.eventdriven.eventsversioning.snapshottesting;

import io.eventdriven.eventsversioning.testing.Contract;
import io.eventdriven.eventsversioning.testing.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

public class ShoppingCartVersioningTests {
  private static final UUID FIXED_CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final OffsetDateTime FIXED_DATE = OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final Contract contract = Contract.specification();

  record ShoppingCartConfirmedV1(
    UUID shoppingCartId,
    String clientId,
    OffsetDateTime confirmedAt
  ) {
  }

  record ShoppingCartConfirmedV2(
    UUID shoppingCartId,
    String clientId,
    OffsetDateTime confirmedAt,
    String initializedBy
  ) {
  }

  @Test
  public void shoppingCartConfirmedV1_withCompleteData_contractIsUnchanged() {
    contract
      .given(new ShoppingCartConfirmedV1(FIXED_CART_ID, "anonymised", FIXED_DATE))
      .whenSerialized()
      .thenContractIsUnchanged();
  }

  @Test
  public void shoppingCartConfirmedV1_withNullClientId_contractIsUnchanged() {
    contract
      .given(new ShoppingCartConfirmedV1(FIXED_CART_ID, null, FIXED_DATE))
      .whenSerialized(Snapshot.forMessageType("ShoppingCartConfirmedV1_NullClientId"))
      .thenContractIsUnchanged();
  }

  @Test
  public void shoppingCartConfirmedV2_withRequiredData_contractIsUnchanged() {
    contract
      .given(new ShoppingCartConfirmedV2(FIXED_CART_ID, "anonymised", FIXED_DATE, null))
      .whenSerialized(Snapshot.forMessageType("ShoppingCartConfirmedV2_WithRequiredData"))
      .thenContractIsUnchanged();
  }

  @Test
  public void shoppingCartConfirmedV2_withCompleteData_contractIsUnchanged() {
    contract
      .given(new ShoppingCartConfirmedV2(FIXED_CART_ID, "anonymised", FIXED_DATE, "Oskar"))
      .whenSerialized()
      .thenContractIsUnchanged();
  }

  @Test
  public void givenV1Event_whenReadAsV2_thenForwardCompatible() {
    contract
      .given(Snapshot.of(ShoppingCartConfirmedV1.class))
      .whenDeserializedAs(ShoppingCartConfirmedV2.class)
      .thenForwardCompatible(v2 -> assertNull(v2.initializedBy()));
  }

  @Test
  public void givenV2Event_whenReadAsV1_thenBackwardCompatible() {
    contract
      .given(new ShoppingCartConfirmedV2(FIXED_CART_ID, "anonymised", FIXED_DATE, "admin"))
      .whenDeserializedAs(ShoppingCartConfirmedV1.class)
      .thenBackwardCompatible();
  }
}
