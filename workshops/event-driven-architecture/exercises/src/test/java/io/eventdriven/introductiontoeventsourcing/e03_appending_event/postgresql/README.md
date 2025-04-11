# Exercise 03 - Appending events to PostgreSQL

Using a defined structure of events from the [first exercise](../../e01_events_definition), fill a `appendEvents` function to store events in [PostgreSQL](../../../eventstores/postgresql/PostgreSQLEventStore.java).

## Prerequisites
Run [docker-compose](../../../../../../../../docker-compose.yml) script from the main workshop repository to start PostgreSQL instance.

```shell
docker compose up
```

After that you can use PgAdmin UI to see how streams and events look like. It's available at: http://localhost:5050/.
