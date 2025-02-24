# Exercise 04 - Getting the current entity state from events using EventStoreDB

Having a defined structure of events and an entity representing the shopping cart from the [first exercise](../../e01_events_definition), fill a `getShoppingCart` function that will rebuild the current state from events.

If needed you can modify the events or entity structure.

There are two variations:
- using mutable entities: [Mutable/GettingStateFromEventsTests.java](./mutable/GettingStateFromEventsTests.java),
- using fully immutable structures: [Immutable/Solution1/GettingStateFromEventsTests.java](./immutable/solution2/GettingStateFromEventsTests.java).

Select your preferred approach (or both) to solve this use case. If needed you can modify entities or events.

## Prerequisites
Run [docker-compose](../../../../../../../../../docker-compose.yml) script from the main workshop repository to start EventStoreDB instance.

```shell
docker compose up
```

or for Mac:

```shell
docker compose -f docker-compose.arm.yml up
```

After that you can use EventStoreDB UI to see how streams and events look like. It's available at: http://localhost:2113/.

## Solution

Read also my articles:
- [How to get the current entity state from events?](https://event-driven.io/en/how_to_get_the_current_entity_state_in_event_sourcing/?utm_source=event_sourcing_java_workshop).
- [Should you throw an exception when rebuilding the state from events?](https://event-driven.io/en/should_you_throw_exception_when_rebuilding_state_from_events/=event_sourcing_java_workshop)
- and [EventStoreDB documentation on reading events](https://developers.eventstore.com/clients/grpc/reading-events.html#reading-from-a-stream)

1. Mutable: [Mutable/GettingStateFromEventsTests.cs](./mutable/GettingStateFromEventsTests.java).
2. Immutable:
- first: [immutable/solution1/GettingStateFromEventsTests.cs](./immutable/solution1/GettingStateFromEventsTests.java).
- second: [immutable/solution2/GettingStateFromEventsTests.cs](./immutable/solution2/GettingStateFromEventsTests.java).
