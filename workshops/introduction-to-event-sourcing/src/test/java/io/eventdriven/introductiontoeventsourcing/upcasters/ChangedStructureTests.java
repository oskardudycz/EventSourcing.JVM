package io.eventdriven.introductiontoeventsourcing.upcasters;

import io.eventdriven.introductiontoeventsourcing.serialization.Serializer;
import io.eventdriven.introductiontoeventsourcing.v1.ShoppingCartEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ChangedStructureTests {
  public record Client(
    UUID id,
    String name
  ) {
    public Client {
      if (name == null) {
        name = "Unknown";
      }
    }
  }

  public record ShoppingCartOpened(
    UUID shoppingCartId,
    Client client
  ) {
  }

  public static ShoppingCartOpened upcast(
    ShoppingCartEvent.ShoppingCartOpened oldEvent
  ) {
    return new ShoppingCartOpened(
      oldEvent.shoppingCartId(),
      new Client(oldEvent.clientId(), null)
    );
  }

  public static ShoppingCartOpened upcast(
    byte[] oldEventJson
  ) {
    var oldEvent = Serializer.deserialize(oldEventJson);

    return new ShoppingCartOpened(
      UUID.fromString(oldEvent.at("/shoppingCartId").asText()),
      new Client(
        UUID.fromString(oldEvent.at("/clientId").asText()),
        null
      )
    );
  }

  @Test
  public void UpcastObjects_Should_BeForwardCompatible() {
    // Given
    var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());

    // When
    var event = upcast(oldEvent);

    // Then
    assertNotNull(event);

    assertEquals(oldEvent.shoppingCartId(), event.shoppingCartId());
    assertNotNull(event.client());
    assertEquals(oldEvent.clientId(), event.client().id());
    assertEquals("Unknown", event.client().name());
  }

  @Test
  public void UpcastJson_Should_BeForwardCompatible() {
    // Given
    var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());

    // When
    var event = upcast(
      Serializer.serialize(oldEvent)
    );

    // Then
    assertNotNull(event);

    assertEquals(oldEvent.shoppingCartId(), event.shoppingCartId());
    assertNotNull(event.client());
    assertEquals(oldEvent.clientId(), event.client().id());
    assertEquals("Unknown", event.client().name());
  }
}
