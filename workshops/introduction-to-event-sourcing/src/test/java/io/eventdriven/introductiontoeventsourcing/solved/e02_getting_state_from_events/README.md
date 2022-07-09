# Exercise 02 - Getting the current entity state from events

Having a defined structure of events and an entity representing the shopping cart from the [previous exercise](../e01_events_definition), fill a `getShoppingCart` function that will rebuild the current state from events.

If needed you can modify the events or entity structure.

There are two variations:
- using mutable entities: [Mutable/GettingStateFromEventsTests.cs](./mutable/GettingStateFromEventsTests.java),
- using fully immutable structures: [Immutable/GettingStateFromEventsTests.cs](./immutable/GettingStateFromEventsTests.java).

Select your preferred approach (or both) to solve this use case.

_**Reminder**: In Event Sourcing, we're rebuilding the current state by applying on it events data in order of appearance_

## Solution

Read also my article [How to get the current entity state from events?](https://event-driven.io/en/how_to_get_the_current_entity_state_in_event_sourcing/?utm_source=event_sourcing_java_workshop).

1. Mutable: [Mutable/GettingStateFromEventsTests.cs](./mutable/GettingStateFromEventsTests.java).
2. Immutable
* Immutable entity using foreach with switch pattern matching [Immutable/Solution1/GettingStateFromEventsTests.cs](./immutable/solution1/GettingStateFromEventsTests.java).
* Fully immutable and functional with linq Aggregate method: [Immutable/GettingStateFromEventsTests.cs](./immutable/solution2/GettingStateFromEventsTests.java).
