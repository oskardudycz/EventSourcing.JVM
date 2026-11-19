package io.eventdriven.eventsversioning.upcasters;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class NewRequiredPropertyFromMetadataTests {
    public record EventMetadata(UUID userId) {}

    public record ShoppingCartOpened(UUID shoppingCartId, UUID clientId, UUID initializedBy) {}

    public static ShoppingCartOpened upcast(
            ShoppingCartEvent.ShoppingCartOpened oldEvent, EventMetadata eventMetadata) {
        return new ShoppingCartOpened(oldEvent.shoppingCartId(), oldEvent.clientId(), eventMetadata.userId());
    }

    public static ShoppingCartOpened upcast(byte[] oldEventJson, byte[] eventMetadataJson) {
        var oldEvent = Serializer.deserialize(oldEventJson);
        var eventMetadata = Serializer.deserialize(eventMetadataJson);

        return new ShoppingCartOpened(
                UUID.fromString(oldEvent.at("/shoppingCartId").asString()),
                UUID.fromString(oldEvent.at("/clientId").asString()),
                UUID.fromString(eventMetadata.at("/userId").asString()));
    }

    @Test
    public void UpcastObjects_Should_BeForwardCompatible() {
        // Given
        var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());
        var eventMetadata = new EventMetadata(UUID.randomUUID());

        // When
        var event = upcast(oldEvent, eventMetadata);

        // Then
        assertThat(event).isNotNull();

        assertThat(event.shoppingCartId()).isEqualTo(oldEvent.shoppingCartId());
        assertThat(event.clientId()).isEqualTo(oldEvent.clientId());
        assertThat(event.initializedBy()).isEqualTo(eventMetadata.userId());
    }

    @Test
    public void UpcastJson_Should_BeForwardCompatible() {
        // Given
        var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());
        var eventMetadata = new EventMetadata(UUID.randomUUID());

        // When
        var event = upcast(Serializer.serialize(oldEvent), Serializer.serialize(eventMetadata));

        // Then
        assertThat(event).isNotNull();

        assertThat(event.shoppingCartId()).isEqualTo(oldEvent.shoppingCartId());
        assertThat(event.clientId()).isEqualTo(oldEvent.clientId());
        assertThat(event.initializedBy()).isEqualTo(eventMetadata.userId());
    }
}
