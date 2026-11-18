package io.eventdriven.introductiontoeventsourcing.e01_events_definition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class EventsDefinitionTests {
    // 1. Define your events and entity here

    @Tag("Exercise")
    @Test
    public void allEventTypes_ShouldBeDefined() {
        var events = new Object[] {
                    // 2. Put your sample events here
                };

        final int expectedEventTypesCount = 5;
        assertEquals(expectedEventTypesCount, events.length);
        assertEquals(
                expectedEventTypesCount,
                Arrays.stream(events)
                        .collect(Collectors.groupingBy(Object::getClass))
                        .size());
    }
}
