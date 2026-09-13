package io.eventdriven.eventsversioning.snapshottesting;

import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.strictland.Json;
import io.eventdriven.strictland.MessageContract;
import io.eventdriven.strictland.MessageSnapshot;
import io.eventdriven.strictland.SnapshotVariant;
import io.eventdriven.strictland.SpecificationOptions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShoppingCartVersioningTests {
  private static final UUID FIXED_CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final OffsetDateTime FIXED_DATE = OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  // Check the bytes the application really ships, so pass its own mapper
  private static final SpecificationOptions options = Json.Jackson.of(Serializer.mapper);

  record ShoppingCartConfirmedV1(
    UUID shoppingCartId,
    @Nullable String clientId,
    OffsetDateTime confirmedAt
  ) {
  }

  record ShoppingCartConfirmedV2(
    UUID shoppingCartId,
    @Nullable String clientId,
    OffsetDateTime confirmedAt,
    // Added as optional, so a stored V1 event that never had it still reads
    @Nullable String initializedBy
  ) {
  }

  record ShoppingCartConfirmedV3(
    UUID shoppingCartId,
    @Nullable String clientId,
    OffsetDateTime confirmedAt,
    // Added as required, so a stored V1 event that never had it cannot be read
    String confirmedBy
  ) {
  }

  @Test
  public void shoppingCartConfirmedV1_withCompleteData_contractIsUnchanged() {
    MessageContract.specification(options)
      .given(new ShoppingCartConfirmedV1(FIXED_CART_ID, "anonymised", FIXED_DATE))
      .whenSerialized()
      .thenContractIsUnchanged();
  }

  @Test
  public void shoppingCartConfirmedV1_withNullClientId_contractIsUnchanged() {
    MessageContract.specification(options)
      .given(new ShoppingCartConfirmedV1(FIXED_CART_ID, null, FIXED_DATE))
      .whenSerializedAs(SnapshotVariant.named("NullClientId"))
      .thenContractIsUnchanged();
  }

  @Test
  public void shoppingCartConfirmedV2_withCompleteData_contractIsUnchanged() {
    MessageContract.specification(options)
      .given(new ShoppingCartConfirmedV2(FIXED_CART_ID, "anonymised", FIXED_DATE, "Oskar"))
      .whenSerialized()
      .thenContractIsUnchanged();
  }

  @Test
  public void shoppingCartConfirmedV2_withRequiredData_contractIsUnchanged() {
    MessageContract.specification(options)
      .given(new ShoppingCartConfirmedV2(FIXED_CART_ID, "anonymised", FIXED_DATE, null))
      .whenSerializedAs(SnapshotVariant.named("WithRequiredData"))
      .thenContractIsUnchanged();
  }

  @Test
  public void givenV1Event_whenReadByV2_thenBackwardCompatible() {
    MessageContract.specification(options)
      .given(MessageSnapshot.of(ShoppingCartConfirmedV1.class))
      .whenDeserializedAs(ShoppingCartConfirmedV2.class)
      .thenBackwardCompatible(v2 -> assertNull(v2.initializedBy()));
  }

  @Test
  public void givenV2Event_whenReadByV1_thenForwardCompatible() {
    MessageContract.specification(options)
      .given(MessageSnapshot.of(ShoppingCartConfirmedV2.class))
      .whenDeserializedAs(ShoppingCartConfirmedV1.class)
      .thenForwardCompatible();
  }

  @Test
  public void givenV1Event_whenReadByV3AddingARequiredField_thenNotBackwardCompatible() {
    var error = assertThrows(AssertionError.class, () ->
      MessageContract.specification(options)
        .given(MessageSnapshot.of(ShoppingCartConfirmedV1.class))
        .whenDeserializedAs(ShoppingCartConfirmedV3.class)
        .thenBackwardCompatible());

    assertTrue(error.getMessage().contains("confirmedBy"), error.getMessage());
  }
}
