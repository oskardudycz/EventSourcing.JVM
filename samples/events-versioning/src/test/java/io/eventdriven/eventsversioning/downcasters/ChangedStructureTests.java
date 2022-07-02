package io.eventdriven.eventsversioning.downcasters;

import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

  public static ShoppingCartEvent.ShoppingCartOpened downcast(
    ShoppingCartOpened newEvent
  ) {
    return new ShoppingCartEvent.ShoppingCartOpened(
      newEvent.shoppingCartId(),
      newEvent.client().id()
    );
  }

  public static ShoppingCartEvent.ShoppingCartOpened downcast(
    byte[] newEventJson
  ) {
    var newEvent = Serializer.deserialize(newEventJson);

    return new ShoppingCartEvent.ShoppingCartOpened(
      UUID.fromString(newEvent.at("/shoppingCartId").asText()),
      UUID.fromString(newEvent.at("/client/id").asText())
    );
  }

  @Test
  public void DowncastObjects_Should_BeForwardCompatible() {
    // Given
    var newEvent = new ShoppingCartOpened(
      UUID.randomUUID(),
      new Client(UUID.randomUUID(), "Oskar the Grouch")
    );

    // When
    var oldEvent = downcast(newEvent);

    // Then
    assertNotNull(oldEvent);

    assertEquals(newEvent.shoppingCartId(), oldEvent.shoppingCartId());
    assertEquals(newEvent.client().id(), oldEvent.clientId());
  }

  @Test
  public void DowncastJson_Should_BeForwardCompatible() {
    // Given
    var newEvent = new ShoppingCartOpened(
      UUID.randomUUID(),
      new Client(UUID.randomUUID(), "Oskar the Grouch")
    );

    // When
    var oldEvent = downcast(
      Serializer.serialize(newEvent)
    );

    // Then
    assertNotNull(oldEvent);

    assertEquals(newEvent.shoppingCartId(), oldEvent.shoppingCartId());
    assertEquals(newEvent.client().id(), oldEvent.clientId());
  }
}
