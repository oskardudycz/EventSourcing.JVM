package io.eventdriven.eventdrivenarchitecture.e01_events_definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class EventsDefinitionTests {
    @Test
    public void guestStayAccountEventTypes_AreDefined() {
        // Given
        var guestStayAccountId = UUID.randomUUID();
        var groupCheckoutId = UUID.randomUUID();

        // When
        var events = List.of(
                new GuestStayAccountEvent.GuestCheckedIn(guestStayAccountId, OffsetDateTime.now()),
                new GuestStayAccountEvent.ChargeRecorded(guestStayAccountId, 100, OffsetDateTime.now()),
                new GuestStayAccountEvent.PaymentRecorded(guestStayAccountId, 100, OffsetDateTime.now()),
                new GuestStayAccountEvent.GuestCheckedOut(guestStayAccountId, OffsetDateTime.now(), groupCheckoutId),
                new GuestStayAccountEvent.GuestCheckoutFailed(
                        guestStayAccountId,
                        GuestStayAccountEvent.GuestCheckoutFailed.FailureReason.INVALID_STATE,
                        OffsetDateTime.now(),
                        groupCheckoutId));

        // Then
        final int expectedEventTypesCount = 5;
        assertThat(events.size()).isEqualTo(expectedEventTypesCount);
        assertThat(events.stream()
                        .collect(Collectors.groupingBy(Object::getClass))
                        .size())
                .isEqualTo(expectedEventTypesCount);
    }

    @Test
    public void groupCheckoutEventTypes_AreDefined() {
        // Given
        var groupCheckoutId = UUID.randomUUID();
        var guestStayAccountIds = new UUID[] {UUID.randomUUID(), UUID.randomUUID()};
        var clerkId = UUID.randomUUID();

        // When
        var events = List.of(
                new GroupCheckoutEvent.GroupCheckoutInitiated(
                        groupCheckoutId, clerkId, guestStayAccountIds, OffsetDateTime.now()),
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(
                        groupCheckoutId, guestStayAccountIds[0], OffsetDateTime.now()),
                new GroupCheckoutEvent.GuestCheckoutFailureRecorded(
                        groupCheckoutId, guestStayAccountIds[1], OffsetDateTime.now()),
                new GroupCheckoutEvent.GroupCheckoutFailed(
                        groupCheckoutId,
                        new UUID[] {guestStayAccountIds[0]},
                        new UUID[] {guestStayAccountIds[1]},
                        OffsetDateTime.now()),
                new GroupCheckoutEvent.GroupCheckoutCompleted(
                        groupCheckoutId, guestStayAccountIds, OffsetDateTime.now()));

        // Then
        final int expectedEventTypesCount = 5;
        assertThat(events.size()).isEqualTo(expectedEventTypesCount);
        assertThat(events.stream()
                        .collect(Collectors.groupingBy(Object::getClass))
                        .size())
                .isEqualTo(expectedEventTypesCount);
    }
}
