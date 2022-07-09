package io.eventdriven.introductiontoeventsourcing.e01_events_definition;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EventsDefinitionTests {
  // 1. Define your events and entity here

  @Tag("Exercise")
  @Test
  public void AllEventTypes_ShouldBeDefined() {
    var events = new Object[]
      {
        // 2. Put your sample events here
      };

    final int expectedEventTypesCount = 5;
    assertEquals(expectedEventTypesCount, events.length);
    assertEquals(expectedEventTypesCount, Arrays.stream(events).collect(Collectors.groupingBy(Object::getClass)).size());
  }
}
