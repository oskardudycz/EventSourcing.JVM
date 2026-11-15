# Exercise 03 - Appending events to MongoDB

Using a defined structure of events from the [first exercise](../../e01_events_definition), fill a `appendEvents` function to store events in [MongoDB](../../../eventstores/mongodb/MongoDBEventStore.java).

## Prerequisites
Have Docker running. The tests start a MongoDB instance with [Testcontainers](https://testcontainers.com/), so you don't need to start it yourself. To see the test data, start `docker compose --profile all up`; the tests then use those containers automatically, as described in [Running](../../../../../../../../../README.md#running).
