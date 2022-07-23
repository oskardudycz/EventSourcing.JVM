# Exercise 04 - Getting the current entity state from events using Occurrent

Having a defined structure of events and an entity representing the shopping cart from the [first exercise](../../e01_events_definition), fill a `getShoppingCart` function that will rebuild the current state from events.

If needed you can modify the events or entity structure.

There are two variations:
- using mutable entities: [Mutable/GettingStateFromEventsTests.java](./mutable/GettingStateFromEventsTests.java),
- using fully immutable structures: [Immutable/Solution1/GettingStateFromEventsTests.java](./immutable/solution2/GettingStateFromEventsTests.java).

Select your preferred approach (or both) to solve this use case. If needed you can modify entities or events.

## Solution

Read also my articles:
- [How to get the current entity state from events?](https://event-driven.io/en/how_to_get_the_current_entity_state_in_event_sourcing/?utm_source=event_sourcing_java_workshop).
- [Should you throw an exception when rebuilding the state from events?](https://event-driven.io/en/should_you_throw_exception_when_rebuilding_state_from_events/=event_sourcing_java_workshop)
- and [Occurrent documentation on reading events](https://occurrent.org/documentation#eventstream)

2. Immutable:
- second: [immutable/GettingStateFromEventsTests.java](./immutable/GettingStateFromEventsTests.java).
