# Exercise 04 - Getting the current entity state from events using EventStoreDB

Having a defined structure of events and an entity representing the shopping cart from the [first exercise](../../e01_events_definition), fill a `getShoppingCart` function that will rebuild the current state from events.

If needed you can modify the events or entity structure.

There are two variations:
- using mutable entities: [Mutable/GettingStateFromEventsTests.cs](./mutable/GettingStateFromEventsTests.java),
- using fully immutable structures: [Immutable/Solution1/GettingStateFromEventsTests.cs](./immutable/GettingStateFromEventsTests.java).

Select your preferred approach (or both) to solve this use case. If needed you can modify entities or events.

## Prerequisites
Run [docker-compose](../../../../../../../../docker-compose.yml) script from the main workshop repository to start EventStoreDB instance.

```shell
docker-compose up
```

After that you can use EventStoreDB UI to see how streams and events look like. It's available at: http://localhost:2113/.
