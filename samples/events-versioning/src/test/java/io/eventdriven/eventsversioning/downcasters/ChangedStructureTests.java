package io.eventdriven.eventsversioning.downcasters;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ChangedStructureTests {
    public record Client(UUID id, String name) {
        public Client {
            if (name == null) {
                name = "Unknown";
            }
        }
    }

    public record ShoppingCartOpened(UUID shoppingCartId, Client client) {}

    public static ShoppingCartEvent.ShoppingCartOpened downcast(ShoppingCartOpened newEvent) {
        return new ShoppingCartEvent.ShoppingCartOpened(
                newEvent.shoppingCartId(), newEvent.client().id());
    }

    public static ShoppingCartEvent.ShoppingCartOpened downcast(byte[] newEventJson) {
        var newEvent = Serializer.deserialize(newEventJson);

        return new ShoppingCartEvent.ShoppingCartOpened(
                UUID.fromString(newEvent.at("/shoppingCartId").asString()),
                UUID.fromString(newEvent.at("/client/id").asString()));
    }

    @Test
    public void DowncastObjects_Should_BeForwardCompatible() {
        // Given
        var newEvent = new ShoppingCartOpened(UUID.randomUUID(), new Client(UUID.randomUUID(), "Oskar the Grouch"));

        // When
        var oldEvent = downcast(newEvent);

        // Then
        assertThat(oldEvent).isNotNull();

        assertThat(oldEvent.shoppingCartId()).isEqualTo(newEvent.shoppingCartId());
        assertThat(oldEvent.clientId()).isEqualTo(newEvent.client().id());
    }

    @Test
    public void DowncastJson_Should_BeForwardCompatible() {
        // Given
        var newEvent = new ShoppingCartOpened(UUID.randomUUID(), new Client(UUID.randomUUID(), "Oskar the Grouch"));

        // When
        var oldEvent = downcast(Serializer.serialize(newEvent));

        // Then
        assertThat(oldEvent).isNotNull();

        assertThat(oldEvent.shoppingCartId()).isEqualTo(newEvent.shoppingCartId());
        assertThat(oldEvent.clientId()).isEqualTo(newEvent.client().id());
    }
}
