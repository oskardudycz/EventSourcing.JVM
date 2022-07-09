package io.eventdriven.introductiontoeventsourcing.simplemappings;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.eventdriven.introductiontoeventsourcing.serialization.Serializer;
import io.eventdriven.introductiontoeventsourcing.v1.ShoppingCartEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RenamedPropertyTests {
  record ShoppingCartOpened(
    @JsonProperty("shoppingCartId") UUID cartId,
    UUID clientId
  ) implements ShoppingCartEvent {
  }

  @Test
  public void Should_BeForwardCompatible()
  {
    // Given
    var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());
    var bytes = Serializer.serialize(oldEvent);

    // When
    var result = Serializer.deserialize(ShoppingCartOpened.class, bytes);

    // Then
    assertTrue(result.isPresent());

    var event = result.get();

    assertEquals(oldEvent.shoppingCartId(), event.cartId());
    assertEquals(oldEvent.clientId(), event.clientId());
  }

  @Test
  public void Should_BeBackwardCompatible()
  {
    // Given
    var event = new ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());
    var bytes = Serializer.serialize(event);

    // When
    var result = Serializer.deserialize(ShoppingCartEvent.ShoppingCartOpened.class, bytes);

    // Then
    assertTrue(result.isPresent());

    var oldEvent = result.get();

    assertEquals(event.cartId(), oldEvent.shoppingCartId());
    assertEquals(event.clientId(), oldEvent.clientId());
  }
}
