package io.eventdriven.eventdrivenarchitecture.e01_events_definition;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventsDefinitionTests {

  @Test
  @Tag("Exercise")
  public void guestStayAccountEventTypes_AreDefined() {
    // Given
    var guestStayId = UUID.randomUUID();
    var groupCheckoutId = UUID.randomUUID();


    // When
    var events = List.of
      (
        // 2. TODO: Put your sample Guest Stay events here
      );

    // Then
    final int expectedEventTypesCount = 5;
    assertEquals(expectedEventTypesCount, events.size());
    assertEquals(expectedEventTypesCount, events.stream().collect(Collectors.groupingBy(Object::getClass)).size());
  }


  @Test
  @Tag("Exercise")
  public void groupCheckoutEventTypes_AreDefined() {
    // Given
    var groupCheckoutId = UUID.randomUUID();
    var guestStayIds = new UUID[]{UUID.randomUUID(), UUID.randomUUID()};
    var clerkId = UUID.randomUUID();

    // When
    var events = List.of
      (
        // 2. TODO: Put your sample Group Checkout events here
      );

    // Then
    final int expectedEventTypesCount = 5;
    assertEquals(expectedEventTypesCount, events.size());
    assertEquals(expectedEventTypesCount, events.stream().collect(Collectors.groupingBy(Object::getClass)).size());
  }
}
