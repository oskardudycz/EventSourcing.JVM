# To-do

- [x] Q1 `ESDBEventStore`: implement `subscribe`, with an integration test
- [x] Q2 `orders/OrderSaga.java`
- [x] 1. Green build, full transcripts
- [x] 2. One `OrderSaga`
- [x] 2b. Bump to Spring Boot 4.1 (Gradle 9.2.1, Jackson 3, log4j2 starter, JSpecify)
- [x] 3. Messaging: event store as outbox, one `MessageBus`
- [ ] 4. Payment settlement
- [ ] 5. Timeouts

Notes:
- Step 1: `ShoppingCartTests` needs EventStoreDB: `docker compose up -d eventstore.db` (allowed by Oskar).
- Step 1: `log4j2.xml` status changed from DEBUG to WARN (agreed).
- Step 1: the SLF4J NOP warning (client error logs went nowhere). Fixed in 2b.
- Step 2: javac "unchecked operations" note existed before (generic varargs in CommandBus/EventBus, EventSerializer, EntityStore, test specs). Most should go with step 3.
- Step 2b: SLF4J warning and javac `When.MAYBE` warning are gone. Gradle prints a "consider enabling configuration cache" hint; not ours.
- Step 3: the e-commerce code no longer adds "unchecked" warnings. The note stays, from `core/commands`, `core/events`, `EntityStore`, `EventSerializer` (hotel management) and the test helper `EventSourcedSpecification`.
- Step 3: `ESDBEventStore.subscribe` starts from the beginning of `$all` each time; it keeps no checkpoint.
- Step 3: `InMemoryEventStore.use` stays as a catch-all subscriber that runs before the others; unit tests use it to catch appended events. It is not on the `EventStore` interface.
- Step 3 follow-up: `OrderPaymentState`/`OrderShipmentState` renamed to `...Status`. `EventStore` has one `subscribe(Class<E>, Consumer<EventEnvelope<E>>)`; `subscribeWithMetadata` and `EventHandler` are gone. Projections subscribe on their own; forwarders only read and throw when the summary is behind the event, so the store redelivers.
