package io.eventdriven.eventsversioning.simplemappings;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.eventdriven.eventsversioning.serialization.Serializer;
import io.eventdriven.eventsversioning.v1.ShoppingCartEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class RenamedPropertyTests {
    record ShoppingCartOpened(
            @JsonProperty("shoppingCartId") UUID cartId, UUID clientId) implements ShoppingCartEvent {}

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

        assertThat(event.cartId()).isEqualTo(oldEvent.shoppingCartId());
        assertThat(event.clientId()).isEqualTo(oldEvent.clientId());
    }

    @Test
    public void Should_BeBackwardCompatible() {
        // Given
        var event = new ShoppingCartOpened(UUID.randomUUID(), UUID.randomUUID());
        var bytes = Serializer.serialize(event);

        // When
        var result = Serializer.deserialize(ShoppingCartEvent.ShoppingCartOpened.class, bytes);

        // Then
        assertThat(result.isPresent()).isTrue();

        var oldEvent = result.get();

        assertThat(oldEvent.shoppingCartId()).isEqualTo(event.cartId());
        assertThat(oldEvent.clientId()).isEqualTo(event.clientId());
    }
}
