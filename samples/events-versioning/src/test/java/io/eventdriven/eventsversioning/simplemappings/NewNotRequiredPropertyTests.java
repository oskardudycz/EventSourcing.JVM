package io.eventdriven.eventsversioning.simplemappings;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class NewNotRequiredPropertyTests {
    public record ShoppingCartOpened(
            UUID shoppingCartId,
            UUID clientId,
            // Adding new not required property
            LocalDateTime initializedAt) {}

    @Test
    public void Should_BeForwardCompatible() {
        // Given
        var oldEvent = new ShoppingCartEvent.ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());
        var bytes = Serializer.serialize(oldEvent);

        // When
        var result = Serializer.deserialize(ShoppingCartOpened.class, bytes);

        // Then
        assertThat(result.isPresent()).isTrue();

        var event = result.get();

        assertThat(event.shoppingCartId()).isEqualTo(oldEvent.shoppingCartId());
        assertThat(event.clientId()).isEqualTo(oldEvent.clientId());
        assertThat(event.initializedAt()).isNull();
    }

    @Test
    public void Should_BeBackwardCompatible() {
        // Given
        var event = new ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now());
        var bytes = Serializer.serialize(event);

        // When
        var result = Serializer.deserialize(ShoppingCartEvent.ShoppingCartOpened.class, bytes);

        // Then
        assertThat(result.isPresent()).isTrue();

        var oldEvent = result.get();

        assertThat(oldEvent.shoppingCartId()).isEqualTo(event.shoppingCartId());
        assertThat(oldEvent.clientId()).isEqualTo(event.clientId());
    }
}
