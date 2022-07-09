package io.eventdriven.introductiontoeventsourcing.simplemappings;

import io.eventdriven.introductiontoeventsourcing.serialization.Serializer;
import io.eventdriven.introductiontoeventsourcing.v1.ShoppingCartEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NewNotRequiredPropertyTests {
  public record ShoppingCartOpened(
    UUID shoppingCartId,
    UUID clientId,
    // Adding new not required property
    LocalDateTime initializedAt
  ) { }

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

    assertEquals(oldEvent.shoppingCartId(), event.shoppingCartId());
    assertEquals(oldEvent.clientId(), event.clientId());
    assertNull(event.initializedAt());
  }

  @Test
  public void Should_BeBackwardCompatible()
  {
    // Given
    var event = new ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now());
    var bytes = Serializer.serialize(event);

    // When
    var result = Serializer.deserialize(ShoppingCartEvent.ShoppingCartOpened.class, bytes);

    // Then
    assertTrue(result.isPresent());

    var oldEvent = result.get();

    assertEquals(event.shoppingCartId(), oldEvent.shoppingCartId());
    assertEquals(event.clientId(), oldEvent.clientId());
  }
}
