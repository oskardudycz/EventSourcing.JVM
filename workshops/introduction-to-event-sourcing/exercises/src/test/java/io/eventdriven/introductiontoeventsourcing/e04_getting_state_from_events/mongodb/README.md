# Exercise 04 - Getting the current entity state from events using MongoDB

Having a defined structure of events and an entity representing the shopping cart from the [first exercise](../../e01_events_definition), fill a `getShoppingCart` function that will rebuild the current state from events.

If needed you can modify the events or entity structure.

There are two variations:
- using mutable entities: [Mutable/GettingStateFromEventsTests.java](./mutable/GettingStateFromEventsTests.java),
- using fully immutable structures: [Immutable/Solution1/GettingStateFromEventsTests.java](./immutable/GettingStateFromEventsTests.java).

Select your preferred approach (or both) to solve this use case. If needed you can modify entities or events.

## Prerequisites
Have Docker running. The tests start a MongoDB instance with [Testcontainers](https://testcontainers.com/), so you don't need to start it yourself. To see the test data, start `docker compose --profile all up`; the tests then use those containers automatically, as described in [Running](../../../../../../../../../README.md#running).
