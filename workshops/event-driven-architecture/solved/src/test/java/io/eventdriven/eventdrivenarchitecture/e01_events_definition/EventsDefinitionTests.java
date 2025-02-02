package io.eventdriven.eventdrivenarchitecture.e01_events_definition;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.eventdriven.eventdrivenarchitecture.e01_events_definition.GroupCheckoutEvent.*;
import static io.eventdriven.eventdrivenarchitecture.e01_events_definition.GuestStayAccountEvent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventsDefinitionTests {
  @Test
  public void guestStayAccountEventTypes_shouldBeDefined() {
    // Given
    var guestStayId = UUID.randomUUID();
    var groupCheckoutId = UUID.randomUUID();

    // When
    var events = new GuestStayAccountEvent[]{
      new GuestCheckedIn(guestStayId, OffsetDateTime.now()),
      new ChargeRecorded(guestStayId, 100.0, OffsetDateTime.now()),
      new PaymentRecorded(guestStayId, 100.0, OffsetDateTime.now()),
      new GuestCheckedOut(guestStayId, groupCheckoutId, OffsetDateTime.now()),
      new GuestStayAccountEvent.GuestCheckoutFailed(
        guestStayId,
        GuestStayAccountEvent.GuestCheckoutFailed.Reason.InvalidState,
        groupCheckoutId,
        OffsetDateTime.now()
      )
    };

    // Then
    final int minimumExpectedEventTypesCount = 5;
    assertEquals(minimumExpectedEventTypesCount, events.length);
    assertEquals(minimumExpectedEventTypesCount,
      Arrays.stream(events).collect(Collectors.groupingBy(Object::getClass)).size());
  }

  @Test
  public void groupCheckoutEventTypes_shouldBeDefined() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var guestStayIds = new UUID[]{UUID.randomUUID(), UUID.randomUUID()};
    var clerkId = UUID.randomUUID();

    // When
    var events = new GroupCheckoutEvent[]{
      new GroupCheckoutInitiated(
        groupCheckoutId, clerkId, guestStayIds, OffsetDateTime.now()
      ),
      new GuestCheckoutsStarted(
        groupCheckoutId, guestStayIds, OffsetDateTime.now()
      ),
      new GuestCheckoutCompleted(
        groupCheckoutId, guestStayIds[0], OffsetDateTime.now()
      ),
      new GroupCheckoutEvent.GuestCheckoutFailed(
        groupCheckoutId, guestStayIds[1], OffsetDateTime.now()
      ),
      new GroupCheckoutFailed(
        groupCheckoutId,
        new UUID[]{guestStayIds[0]},
        new UUID[]{guestStayIds[1]},
        OffsetDateTime.now()
      ),
      new GroupCheckoutCompleted(
        groupCheckoutId, guestStayIds, OffsetDateTime.now()
      )
    };

    // Then
    final int minimumExpectedEventTypesCount = 6;
    assertEquals(minimumExpectedEventTypesCount, events.length);
    assertEquals(minimumExpectedEventTypesCount,
      Arrays.stream(events).collect(Collectors.groupingBy(Object::getClass)).size());
  }
}
