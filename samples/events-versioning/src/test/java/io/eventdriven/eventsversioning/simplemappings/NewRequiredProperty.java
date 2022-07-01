package io.eventdriven.eventsversioning.simplemappings;

import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NewRequiredProperty {
  public enum ShoppingCartStatus {
    Pending,
    Opened,
    Confirmed,
    Cancelled
  }

  public record ShoppingCartOpened(
    UUID shoppingCartId,
    UUID clientId,
    // Adding new not required property as nullable
    ShoppingCartStatus status
  ) {
    public ShoppingCartOpened {
      if (status == null) {
        status = ShoppingCartStatus.Opened;
      }
    }
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

    assertEquals(oldEvent.shoppingCartId(), event.shoppingCartId());
    assertEquals(oldEvent.clientId(), event.clientId());
    assertEquals(ShoppingCartStatus.Opened, event.status());
  }

  @Test
  public void Should_BeBackwardCompatible()
  {
    // Given
    var event = new ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID(), ShoppingCartStatus.Pending);
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
