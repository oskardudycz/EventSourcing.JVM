# Exercise 03 - Appending events to MongoDB

Using a defined structure of events from the [first exercise](../../e01_events_definition), fill a `appendEvents` function to store events in [MongoDB](../../../eventstores/mongodb/MongoDBEventStore.java).

## Prerequisites
Run [docker-compose](../../../../../../../../docker-compose.yml) script from the main workshop repository to start MongoDB instance.

```shell
docker compose up
```

After that you can use MongoDB UI to see how streams and events look like. It's available at: http://localhost:8081/.
