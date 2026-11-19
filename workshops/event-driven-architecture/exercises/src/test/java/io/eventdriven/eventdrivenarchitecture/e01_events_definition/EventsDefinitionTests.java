package io.eventdriven.eventdrivenarchitecture.e01_events_definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class EventsDefinitionTests {

    @Test
    @Tag("Exercise")
    public void guestStayAccountEventTypes_AreDefined() {
        // Given
        var guestStayId = UUID.randomUUID();
        var groupCheckoutId = UUID.randomUUID();

        // When
        var events = List.of(
                // 2. TODO: Put your sample Guest Stay events here
                );

        // Then
        final int expectedEventTypesCount = 5;
        assertThat(events.size()).isEqualTo(expectedEventTypesCount);
        assertThat(events.stream()
                        .collect(Collectors.groupingBy(Object::getClass))
                        .size())
                .isEqualTo(expectedEventTypesCount);
    }

    @Test
    @Tag("Exercise")
    public void groupCheckoutEventTypes_AreDefined() {
        // Given
        var groupCheckoutId = UUID.randomUUID();
        var guestStayIds = new UUID[] {UUID.randomUUID(), UUID.randomUUID()};
        var clerkId = UUID.randomUUID();

        // When
        var events = List.of(
                // 2. TODO: Put your sample Group Checkout events here
                );

        // Then
        final int expectedEventTypesCount = 5;
        assertThat(events.size()).isEqualTo(expectedEventTypesCount);
        assertThat(events.stream()
                        .collect(Collectors.groupingBy(Object::getClass))
                        .size())
                .isEqualTo(expectedEventTypesCount);
    }
}
