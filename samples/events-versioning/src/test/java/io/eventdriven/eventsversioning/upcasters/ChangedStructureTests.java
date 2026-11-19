package io.eventdriven.eventsversioning.upcasters;

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

    public static ShoppingCartOpened upcast(ShoppingCartEvent.ShoppingCartOpened oldEvent) {
        return new ShoppingCartOpened(oldEvent.shoppingCartId(), new Client(oldEvent.clientId(), null));
    }

    public static ShoppingCartOpened upcast(byte[] oldEventJson) {
        var oldEvent = Serializer.deserialize(oldEventJson);

        return new ShoppingCartOpened(
                UUID.fromString(oldEvent.at("/shoppingCartId").asString()),
                new Client(UUID.fromString(oldEvent.at("/clientId").asString()), null));
    }

    @Test
    public void UpcastObjects_Should_BeForwardCompatible() {
        // Given
        var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());

        // When
        var event = upcast(oldEvent);

        // Then
        assertThat(event).isNotNull();

        assertThat(event.shoppingCartId()).isEqualTo(oldEvent.shoppingCartId());
        assertThat(event.client()).isNotNull();
        assertThat(event.client().id()).isEqualTo(oldEvent.clientId());
        assertThat(event.client().name()).isEqualTo("Unknown");
    }

    @Test
    public void UpcastJson_Should_BeForwardCompatible() {
        // Given
        var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());

        // When
        var event = upcast(Serializer.serialize(oldEvent));

        // Then
        assertThat(event).isNotNull();

        assertThat(event.shoppingCartId()).isEqualTo(oldEvent.shoppingCartId());
        assertThat(event.client()).isNotNull();
        assertThat(event.client().id()).isEqualTo(oldEvent.clientId());
        assertThat(event.client().name()).isEqualTo("Unknown");
    }
}
