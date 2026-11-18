package io.eventdriven.eventdrivenarchitecture.e01_events_definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class EventsDefinitionTests {
    @Test
    public void guestStayAccountEventTypes_AreDefined() {
        // Given
        var guestStayId = UUID.randomUUID();
        var groupCheckoutId = UUID.randomUUID();

        // When
        var events = List.of(
                new GuestStayAccountEvent.GuestCheckedIn(guestStayId, OffsetDateTime.now()),
                new GuestStayAccountEvent.ChargeRecorded(guestStayId, 100, OffsetDateTime.now()),
                new GuestStayAccountEvent.PaymentRecorded(guestStayId, 100, OffsetDateTime.now()),
                new GuestStayAccountEvent.GuestCheckedOut(guestStayId, OffsetDateTime.now(), groupCheckoutId),
                new GuestStayAccountEvent.GuestCheckoutFailed(
                        guestStayId,
                        GuestStayAccountEvent.GuestCheckoutFailed.Reason.InvalidState,
                        OffsetDateTime.now(),
                        groupCheckoutId));

        // Then
        final int expectedEventTypesCount = 5;
        assertEquals(expectedEventTypesCount, events.size());
        assertEquals(
                expectedEventTypesCount,
                events.stream().collect(Collectors.groupingBy(Object::getClass)).size());
    }

    @Test
    public void groupCheckoutEventTypes_AreDefined() {
        // Given
        var groupCheckoutId = UUID.randomUUID();
        var guestStayIds = new UUID[] {UUID.randomUUID(), UUID.randomUUID()};
        var clerkId = UUID.randomUUID();

        // When
        var events = List.of(
                new GroupCheckoutEvent.GroupCheckoutInitiated(
                        groupCheckoutId, clerkId, guestStayIds, OffsetDateTime.now()),
                new GroupCheckoutEvent.GuestCheckoutCompletionRecorded(
                        groupCheckoutId, guestStayIds[0], OffsetDateTime.now()),
                new GroupCheckoutEvent.GuestCheckoutFailureRecorded(
                        groupCheckoutId, guestStayIds[1], OffsetDateTime.now()),
                new GroupCheckoutEvent.GroupCheckoutFailed(
                        groupCheckoutId,
                        new UUID[] {guestStayIds[0]},
                        new UUID[] {guestStayIds[1]},
                        OffsetDateTime.now()),
                new GroupCheckoutEvent.GroupCheckoutCompleted(groupCheckoutId, guestStayIds, OffsetDateTime.now()));

        // Then
        final int expectedEventTypesCount = 5;
        assertEquals(expectedEventTypesCount, events.size());
        assertEquals(
                expectedEventTypesCount,
                events.stream().collect(Collectors.groupingBy(Object::getClass)).size());
    }
}
