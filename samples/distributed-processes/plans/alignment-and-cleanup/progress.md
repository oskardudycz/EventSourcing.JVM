
## Step 1

Done. Tracing bridge moved to `implementation`, price calculator made thread-safe, order page shows "being placed" and refreshes every 2 seconds until the status row exists, catalog preview and Playwright use port 3001, HOWTO and README explain the `correlationId` and the two traces. `./gradlew build eventCatalogTest` passed after the last change (14m 26s, 26 catalog tests). Review: clean after one re-review; the one clarity note on `orderPageShowsTheOrderOnceItHasAStatus` is left open.

## Step 2

Done, review notes open. Event stores use `ExpectedStreamVersion`, long versions, `AppendResult`/`ReadResult` and `Checkpoint` in `core/eventstore`; `EventMetadata` has `transactionId`. PostgreSQL appends through `append_to_stream` (tables `streams`, `messages`). In-memory store is locked, middleware is a plain list. Stores throw `UnreadableMessageException` on read. Packages renamed, dead code deleted, F1 ticked, HOWTO and README patched. Open for the user (review `reviews/20261122T020000_step2_review.md`): negative client version as `at(-1)`, empty append differs by store, no direct test that the PostgreSQL subscription constructor skips the checkpoint. The test `eventAlreadyPastTheCheckpointIsSkippedAndRolledBack` warms up the subscription first because of that constructor change.

Step 2 CI and review: re-review clean on both axes (`reviews/20261122T031500_step2_review.md`); open findings Step-1 and Step-2 moved to follow-ups F1 and F2 for Oskar. `./gradlew build eventCatalogTest` passed together with the CI speed-up below: 6m 01s, all tests and the 26 catalog tests.

## CI speed-up (Q13, Q16, Q17; before step 3)

Done at Oskar's request before the step 2 CI run. `maxParallelForks = 2`; `MessageBus.awaitCaughtUp(Duration)` on the three buses (Kafka checks committed offsets through a `RetryTemplate`, RabbitMQ sends a probe through each queue, in memory returns at once); `MessageBusTests.awaitLog` waits for catch-up instead of a 2-second window; `NoOtlpEndpointTests` flushes the exporters instead of sleeping 5 seconds; Kafka test containers have no consumer group join delay (`TestDatabases.newKafkaContainer()`); deleted the tests of the tools themselves (RabbitMQ dead-letter outage, blocked publisher, connection loss, paused Kafka, Testcontainers detection, Grafana export, the 300-round close race). CI went from 14m 26s to 6m 01s (test class time 746 s to 477 s). Three JVMs overloaded the machine (KurrentDB and Kafka timeouts), and shared Docker Compose containers did not help; both were measured and dropped.
