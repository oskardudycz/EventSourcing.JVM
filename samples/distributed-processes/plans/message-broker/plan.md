# Plan: Real message brokers, event stores and observability for the e-commerce sample

Revision: 10. Built from spec r23.

Spec: `spec.md`. Q&A: `qa.md`. Reviews: `reviews/20261120T073425_spec_r14_review.md`, `reviews/20261120T084619_plan_r2_review.md`.

All paths are relative to `samples/distributed-processes`. `M:` is `src/main/java/io/eventdriven/distributedprocesses/`, `T:` is `src/test/java/io/eventdriven/distributedprocesses/`, `TT:` is `src/test/java/io/eventdriven/testing/`, and `W:` is `workshops/event-driven-architecture/solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses/` from the repository root.

## Terms

- **Channel:** a named destination that modules publish to, such as `payments-requests`. A topic on Kafka, a topic exchange on RabbitMQ.
- **Queue:** a named consumer bound to a channel with a pattern (`*`, `#`). A consumer group on Kafka (on its own topic for a narrower pattern than `#`), a quorum queue on RabbitMQ.
- **Route:** a `MessageRoutes` entry that gives a message type its channel and its routing key. The module whose config holds the route also declares the route's channel with `channel(name)`.
- **Routing key:** the dot-separated key a route gives a message, made with `Urns.routingKeyOf`. On Kafka it is also the record key.
- **Routing consumer:** the Kafka consumer that reads a channel and writes each message whose routing key matches a queue's narrower pattern to that queue's topic.
- **Settings:** `messaging.bus`, `eventstore.type`, `projections.database`, and the `in-memory` Spring profile that sets all three to in memory.
- **The five values:** `messageId`, `correlationId`, `causationId`, `traceId`, `spanId`, resolved as in Emmett's Almanac.
- **Step span:** one span per handler call, opened by the bus's consumer or the store's subscription around the handler, named after the message type.
- **Subscription:** one `EventStore.subscribe(name, types, handler)` per subscriber object (projection, forwarder, saga, client).
- **Checkpoint:** a row of `processor_checkpoints` in the projections database: `processor_id`, `last_processed_position`, `last_processed_transaction_id`.
- **Shared test set:** one abstract test class per seam (`MessageBus`, `EventStore`) that each implementation's test class extends.
- **A criterion that names several implementations** ("on each broker", "on each store") is copied whole into each step that adds one of them. Each step says which sub-criteria it tests and names the step that covers the others; the last step that adds an implementation completes the criterion.
- **Own server:** a test that stops, pauses or alarms a broker starts its own Testcontainers container for that test class, never the shared one or the developer's Docker Compose service, and leaves no change behind.

## Order

Run the steps one after another, in the main working tree. No two steps run in parallel: they share one Gradle build, and several edit the same files (`ECommerceApplicationConfig`, the settings, `build.gradle`).

Interim defaults: until step 15, `messaging.bus` and `eventstore.type` default to `in-memory` and `projections.database` to `h2`, so `bootRun` works without Docker after every step. Each step that adds an implementation adds its value to the allowed values that criterion 5 checks.

---

## Step 1. Explicit routes and declared channels on the in-memory bus

```text
Use the `implement-step` skill.

What to build
Every message type that the four modules publish has an explicit route with a channel and a routing key, and each route's channel is declared. Publishing a message with no route, or to a channel nobody declared, fails with an error that names the message type or the channel, instead of being skipped.

Context
- `M:core/messaging/MessageBus.java` (routes, bind, subscribe, publish, use). It gets `channel(name)`, returning the bus.
- `M:core/messaging/InMemoryMessageBus.java:26` skips a message with no route target (`if (route.target() == null) continue;`).
- `M:core/messaging/MessageRoutes.java` has `route`, `routeTarget`, `routeKey`. Keys use `Urns.routingKeyOf` (`M:core/identifiers/Urns.java`).
- Module configs: `M:ecommerce/payments/PaymentsConfig.java:45-53`, `M:ecommerce/shipments/ShipmentsConfig.java:38-46`, `M:ecommerce/orders/OrdersConfig.java:48`, `M:ecommerce/shoppingcarts/ShoppingCartsConfig.java:44-48`. Only `PaymentExternalEvent` and `ShoppingCartCheckedOut` routes set a key today. The module whose config holds a route declares its channel (for example `PaymentsConfig` declares `payments-requests`).
- Each command and internal event carries its entity ID: `PaymentId`, `OrderId`, `ShipmentId`; `ReserveStock` carries `referenceId` (the order's URN).
- `MessageRouter.firstRecordComponent` stays in `MessageRouter` for its own tests; no module uses it.

Similar code
- The existing key routes in `PaymentsConfig` (`Urns.routingKeyOf(event.referenceId())`) and `ShoppingCartsConfig`.
- Tests: `T:core/messaging/InMemoryMessageBusTests.java`, `TT:MessageCatcherTests.java`.

Blocked by
None.

Acceptance criteria
- 15. Every message type has an explicit route with a channel and a key, so that no message is dropped and Kafka keeps one entity's messages in order.
- 15.1. GIVEN two messages about the same payment WHEN they are published to a Kafka channel THEN both have the payment's routing key as the record key AND reach the same partition.
- 15.2. GIVEN the routes of the four modules WHEN each message type that the modules publish through the bus is routed THEN it gets a channel AND a routing key.
- 15.3. GIVEN a message type with no route WHEN it is published THEN the publish fails with an error that names the message type, on in-memory, Kafka and RabbitMQ.

Tests
This step tests 15.2 and 15.3 on the in-memory bus. 15.1 is covered in step 8 (Kafka); 15.3 also in steps 8 and 10.
- 15.2: everyPublishedMessageTypeHasAChannelAndARoutingKey - calls the four modules' `configure` on one in-memory bus with the existing fakes, then for one instance of every message type the modules publish through the bus (the permitted types of `PaymentCommand`, `ShipmentCommand` and `OrderCommand` that go through the bus, the modules' internal events, `PaymentExternalEvent`, `ShipmentExternalEvent`, `ShoppingCartExternalEvent`) asserts the router gives a non-null channel and a non-null routing key.
- 15.3: publishingAMessageWithoutARouteFailsNamingItsType - asserts `publish` throws and the message names the class.
- extra: publishingToAChannelThatIsNotDeclaredFailsNamingTheChannel.
- extra: aCommandIsRoutedWithTheRoutingKeyOfItsEntity - `CapturePayment` with payment `urn:ecommerce:payment:<uuid>` gets key `ecommerce.payment.<uuid>`.
- extra: reserveStockIsRoutedWithTheRoutingKeyOfItsOrder.

Existing tests this breaks
- `InMemoryMessageBusTests.aMessageWithoutARouteReachesOnlyTheMiddleware`: replaced by the 15.3 test.
- `InMemoryMessageBusTests` and `TT:MessageCatcherTests.java` cases that publish to an undeclared channel: they declare it.
- `T:ecommerce/orders/OrderSagaTests.java`, `PaymentExternalEventForwarderTests.forwarderRefusesAPaymentWithoutSummary...` (line 133) and `ShoppingCartExternalEventForwarderTests.forwarderRefusesASummary...` (line 104) build a bare bus: each test registers, in its own setup, the routes and channels of the messages it publishes (`bus.routes(...)`, `bus.channel(...)`), with no subscribers, so their exact message lists don't change.
- The tests that call a module's `configure` keep passing, because the configs declare their channels.
```

---

## Step 2. The settings and the `in-memory` profile

```text
Use the `implement-step` skill.

What to build
The application reads `messaging.bus` and `eventstore.type` and refuses an unknown value, naming the setting and its allowed values. In this step both accept only `in-memory`, and both default to it. The `in-memory` Spring profile sets both to `in-memory`. Every Spring test in the sample runs with that profile.

Context
- `M:ecommerce/ECommerceApplicationConfig.java` creates `new InMemoryEventStore()` and `new InMemoryMessageBus()`; it chooses them from the settings.
- `src/main/resources/application.properties`; the profile file is `application-in-memory.properties`.
- `TT:ApiSpecification.java` (the base of the 11 API test classes in `T:ecommerce/api/`) and the six `@SpringBootTest` classes in `T:ecommerce/web/` (`AsyncApiTests`, `SpringwolfPublishingTests`, `ShopInBrowserTests`, `ShopPagesTests`, `SandboxCheckoutPageTests`, `OpenApiTests`) get `@ActiveProfiles("in-memory")`.
- `build.gradle` `openApi` `customBootRun` (used by `eventCatalogTest`) runs with `--spring.profiles.active=in-memory`.

Similar code
- Spring Boot `@ConfigurationProperties` with validation; `T:ecommerce/web/OpenApiTests.java` for a context test.

Blocked by
None.

Acceptance criteria
- 5. A developer sets an unknown value for a switch, so that a typo is found at once.
- 5.1. GIVEN `messaging.bus`, `eventstore.type` or `projections.database` is set to a value outside its allowed values (for example `messaging.bus=kafak`) WHEN the application starts THEN the start fails with an error that names the setting and its allowed values, for each of the three settings.

Tests
This step tests 5.1 for `messaging.bus` and `eventstore.type`. Step 3 adds `projections.database`.
- 5.1: startFailsNamingTheSettingAndItsAllowedValues - parameterized over `messaging.bus=kafak` and `eventstore.type=kurentdb`; asserts the context fails to start and the error names the setting and lists `in-memory`.
- extra: inMemoryProfileSelectsTheInMemoryBusAndStore - with the profile, the beans are `InMemoryMessageBus` and `InMemoryEventStore`.

Existing tests this breaks
None. The profile changes nothing yet; the tests get it now so that step 15's defaults don't move them.
```

---

## Step 3. Projections on JPA

```text
Use the `implement-step` skill.

What to build
The projections store their documents through JPA, on H2 or PostgreSQL, chosen by `projections.database` (`h2`, `postgresql`; default `h2` until step 15; `h2` under the `in-memory` profile). Each view's document is a JPA entity with a `version` column for the stream position, and each view has a Spring Data repository with the query methods it needs. `Database` goes.

Context
- `M:core/projections/Database.java` (HashMap with `store`, `get`, `delete`, `find(Class, Predicate)`) and `M:core/projections/Projection.java` (`handle` checks `getVersion() >= streamPosition`, then `evolve`, then stores or deletes).
- The ten documents are classes that implement `Versioned`: `PendingPayment`, `AuthorizedPayment`, `PaymentSummary`, `PendingOrder`, `OrderAwaitingPayment`, `CustomerOrder`, `OrderStatus`, `CustomerShoppingCart`, `ShoppingCartSummary`, `Cart`. Their views call `find(Predicate)`.
- The module configs create `new Database()` (for example `M:ecommerce/orders/OrdersConfig.java:58,71-72`); `ECommerceConfig.configure` and the modules' `Config` classes take the repositories instead.
- `application.properties` excludes the JPA, data source and repository auto-configuration; the exclusions go. `build.gradle` has `spring-boot-starter-data-jpa` and `org.postgresql:postgresql`; H2 is added.
- `TT:TestDatabases.java` finds KurrentDB from the `eventsourcing` Docker Compose project, else starts `KurrentDBContainer`. PostgreSQL is found the same way (Compose service `postgres`, else one shared Testcontainers PostgreSQL).
- Worker views (`PendingPayments.timedOutAt`, `AuthorizedPayments.expiredAt`, `OrdersAwaitingPayment.overdueAt`) return every matching document of the table. On a shared database, worker tests see other tests' documents.

Similar code
- `T:core/projections/ProjectionTests.java` and the view tests, for example `T:ecommerce/orders/api/OrderStatusesTests.java`.
- `TestDatabases.kurrentDBConnectionString()` for the Compose-or-Testcontainers lookup.

Blocked by
Step 2.

Acceptance criteria
- 5. A developer sets an unknown value for a switch, so that a typo is found at once.
- 5.1. GIVEN `messaging.bus`, `eventstore.type` or `projections.database` is set to a value outside its allowed values (for example `messaging.bus=kafak`) WHEN the application starts THEN the start fails with an error that names the setting and its allowed values, for each of the three settings.
- 29. A projection stores and changes a document, so that the view works on JPA.
- 29.1. GIVEN no document WHEN the first event arrives THEN the document is stored at the event's stream position, on PostgreSQL and on H2.
- 29.2. GIVEN a stored document WHEN an event that the document already applied is delivered again THEN the document doesn't change, on PostgreSQL and on H2.
- 29.3. GIVEN a stored document WHEN `evolve` returns no document THEN the document is deleted, on PostgreSQL and on H2.
- 29.4. GIVEN documents of one view WHEN the view queries them through its repository THEN it gets only the matching documents, on PostgreSQL and on H2.

Tests
This step completes 5.1 with `projections.database`, and tests 29 on both databases.
- 5.1: startFailsNamingTheSettingAndItsAllowedValues - adds `projections.database=mysql`; asserts the error lists `h2` and `postgresql`.
- 29.1: firstEventStoresTheDocumentAtTheEventsStreamPosition - on PostgreSQL and on H2.
- 29.2: anEventTheDocumentAlreadyAppliedChangesNothing - on PostgreSQL and on H2.
- 29.3: evolveReturningNoDocumentDeletesIt - on PostgreSQL and on H2.
- 29.4: aViewQueryReturnsOnlyTheMatchingDocuments - every view test runs its query methods on PostgreSQL and on H2.
- extra: inMemoryProfileStoresProjectionsInH2 - under the profile, a stored document is read back from H2.

Existing tests this breaks
- `ProjectionTests` and the ten view tests use `new Database()`: they get repositories from a Spring JPA context and run on both databases.
- `ECommerceTests`, `PaymentExternalEventForwarderTests`, `ShoppingCartExternalEventForwarderTests`, and the tests that call a module's `configure` directly (`PaymentFacadeTests`, `CaptureGatewayClientTests`, `AuthorizationGatewayClientTests`, `PaymentTimeoutWorkerTests`, `AuthorizationExpiryWorkerTests`, `PaymentExternalEventForwarderOrderingTests`, `OrderFacadeTests`, `ShoppingCartFacadeTests`, `FulfillmentServiceClientTests`, `ShipmentExternalEventForwarderTests`, `ShipmentsConfigTests`, `ShipmentFacadeTests`): they keep their scenarios and get their repositories from a Spring context with the `in-memory` profile, on H2.
- Worker tests (`PaymentTimeoutWorkerTests`, `AuthorizationExpiryWorkerTests`, the unpaid-orders tests): they assert only on the messages about their own new IDs, because the shared table holds other tests' documents. Commands the worker publishes for other tests' IDs reach the test's own bus and store, where those streams don't exist, and the aggregates ignore them.
```

---

## Step 4. The five values on events and messages, in memory

```text
Use the `implement-step` skill.

What to build
Every stored event and every message carries `messageId`, `correlationId`, `causationId`, `traceId` and `spanId`. The IDs resolve as in Emmett: a step with nothing to inherit generates the `correlationId`; `causationId` is the handled message's `messageId`; a step with no cause uses its own `correlationId`. A message a handler publishes gets a name-based UUID from the handled message's `messageId`, its type and its position in the published list; a message with no handled message gets a random UUID. `EventStore.read` returns each event's metadata. Tracing is on (Micrometer Tracing with the OpenTelemetry bridge, `management.tracing.sampling.probability=1.0`), with an in-memory span exporter in tests. `KurrentDBEventStore` maps the plain names to `$correlationId`, `$causationId`, `$traceId`, `$spanId` and back.

Context
- `M:core/messaging/EventMetadata.java` is `record EventMetadata(String streamId, long streamPosition, long logPosition)`; `M:core/messaging/EventEnvelope.java` wraps it.
- `M:core/kurrentdb/EventStore.java` (`ReadResult.Success(Object[] events)`), `InMemoryEventStore.java`, `KurrentDBEventStore.java`, `M:core/aggregates/AggregateStore.java` (replays `read`).
- `M:core/messaging/InMemoryMessageBus.java` delivers synchronously. A subscriber's handler runs inside the delivered message's values, so what it appends carries them. Tests observe the values through the events the handlers append (read back with their metadata), not through a new bus API.
- KurrentDB facts, measured in step 4: the Java client 1.2.1 builds its `streams.append` span on its own worker thread, so with a registered global OpenTelemetry the span is a root span with a new trace ID, and the client overwrites `$traceId` and `$spanId` with it. Without the global, the client writes nothing and the store's values stay. See open item P3.

Similar code
- `W:sagas_kafka/core/messages/MessageMetadata.java` for the values on a message (its random `messageId` on each publish is not copied).
- `W:core/InMemorySpansConfiguration.java` for the in-memory span exporter.
- Emmett's rules: `trace-propagation/findings.md` sections 4.2 and 4.3.

Blocked by
Step 1.

Acceptance criteria
- 28. A module appends an event inside a traced step, so that the metadata is stored with the event.
- 28.1. GIVEN a current span AND a `correlationId` and a `causationId` WHEN an event is appended THEN reading it back gives the same `messageId`, `correlationId`, `causationId`, `traceId` and `spanId`, on each store.
- 28.2. GIVEN an event appended to KurrentDB WHEN its raw metadata is read with the KurrentDB client THEN it has `$correlationId`, `$causationId`, `$traceId` and `$spanId`.
- 35. An event is delivered twice, so that its copies are the same message.
- 35.1. GIVEN an event delivered again after a failure WHEN the forwarder publishes it again THEN both copies have the same `messageId`.
- 35.2. GIVEN the saga handles `OrderPlaced` AND publishes `AuthorizePayment` and `ReserveStock` WHEN `OrderPlaced` is delivered again THEN the saga publishes them again with the same two `messageId` values AND the two values differ from each other.

Tests
This step tests 28.1 on in-memory and KurrentDB, 28.2, and 35 on the in-memory bus. Step 7 adds 28.1 on PostgreSQL; steps 8 and 10 add 35 on the brokers.
- 28.1: appendedEventKeepsItsFiveValues - inside a test span, with `correlationId` `c-1` and `causationId` `m-1` in the context, append and read back; asserts the five values match on both stores. The application doesn't register the global OpenTelemetry in this step, so the KurrentDB client writes no trace keys of its own (open item P3).
- 28.2: kurrentDBMetadataUsesItsDollarNames - reads the raw metadata with the KurrentDB client; asserts the keys `$correlationId`, `$causationId`, `$traceId`, `$spanId`.
- 35.1: forwarderRepublishesARedeliveredEventWithTheSameMessageId - in memory; a subscriber of the published external event appends an event and fails on its first call, so the publish fails and the store delivers the stored event again; asserts the two events the subscriber appended carry the same `causationId`, which is the published message's `messageId`.
- 35.2: sagaRepublishesBothCommandsWithTheSameIdsOnRedelivery - `OrderPlaced` delivered twice; the payments and shipments handlers append on each delivery; asserts the `causationId` of the events caused by `AuthorizePayment` is the same on both deliveries, the same for `ReserveStock`, and the two values differ.
- extra: aStepWithNothingToInheritGeneratesTheCorrelationId - events appended by an HTTP command have a new `correlationId` and `causationId` equal to it.
- extra: aHandledMessagePassesItsCorrelationIdOnAndBecomesTheCause - the events a subscriber appends carry the handled message's `correlationId`, and their `causationId` is the handled message's `messageId`.
- extra: readReturnsEachEventsMetadata - on in-memory and KurrentDB.

Existing tests this breaks
- Callers of `ReadResult.Success(Object[] events)`: `AggregateStoreTests`, `InMemoryEventStoreTests.appendStoresEventsThatCanBeReadBack`, `KurrentDBEventStoreTests`. They read events from the new shape.
- Tests that build `EventMetadata(streamId, streamPosition, logPosition)` by hand: they pass the five values.
```

---

## Step 5. One named subscription per subscriber

```text
Use the `implement-step` skill.

What to build
`EventStore.subscribe` takes a subscription name and the event types, for example `subscribe("payments-external-forwarder", List.of(PaymentAuthorized.class, PaymentCaptured.class), forwarder::handle)`. Each subscriber object (each projection, each forwarder, the saga, each client) has one subscription, so on KurrentDB one subscriber gets its events in log order. This prepares the checkpoints of step 6.

Context
- `M:core/kurrentdb/EventStore.java:15` (`subscribe(Class<Event> type, Consumer<EventEnvelope<Event>> handler)`).
- The modules have 12 `eventStore.subscribe(` lines and more chained calls (for example `M:ecommerce/payments/PaymentsConfig.java:85-89`); projections subscribe once per type in a loop over `canHandle()`.
- `M:core/kurrentdb/KurrentDBEventStore.java:82-101` makes one `subscribeToAll` per call with an event type regex filter; with several types the filter matches any of them.

Similar code
- `M:core/projections/Projection.java` `canHandle()`.

Blocked by
Step 4.

Acceptance criteria
None. This step prepares step 6.

Tests
- extra: aSubscriptionGetsEveryEventOfItsTypesInLogOrder - on KurrentDB, one subscription for two types; append A of type 1, B of type 2, C of type 1; asserts it receives A, B, C in that order.
- extra: aProjectionGetsItsEventsInStreamOrderOnKurrentDB - append `OrderPlaced` then `OrderPaymentAuthorized`; asserts the document applied both.

Existing tests this breaks
- `InMemoryEventStoreTests`, `KurrentDBEventStoreTests`, `AggregateStoreTests:159` and the module tests that subscribe by hand: they use the new signature.
```

---

## Step 6. KurrentDB as the event store, with checkpoints

```text
Use the `implement-step` skill.

What to build
`eventstore.type=kurrentdb` runs the sample on KurrentDB. Each subscription stores a checkpoint in the projections database and continues after it on start. A projection stores its document change and its checkpoint in one transaction; a forwarder or the saga publishes and then stores the checkpoint. A checkpoint already at or past the new position skips the update and rolls its transaction back. A subscription whose handler fails stops and resubscribes from its checkpoint after `RetryBackOff`. The in-memory store keeps no checkpoint. One shared `EventStore` test set runs for in-memory and KurrentDB.

Context
- `M:core/kurrentdb/KurrentDBEventStore.java:83-84` uses `.fromStart()`; `M:core/kurrentdb/subscriptions/KurrentDBSubscription.java` has no error handling, retries or resubscribe.
- `M:ecommerce/ECommerceApplicationConfig.java` chooses the store from `eventstore.type`; `kurrentdb` joins the allowed values.
- The checkpoint entity and its Spring Data repository live next to the projections, mapping `processor_checkpoints`.
- With H2 projections and a durable store, checkpoints are gone after a restart and subscriptions replay; that is a known limit (spec, Event stores).

Similar code
- `W:core/RetryBackOff.java` and `W:core/RetryBackOffTest.java` (both ported; the test is one of the workshop's solved checks).
- `T:core/kurrentdb/InMemoryEventStoreTests.java` (21 tests) and `KurrentDBEventStoreTests.java`.
- Emmett's checkpoints, simplified: https://event-driven.io/en/checkpointing_message_processing/.

Blocked by
Steps 3 and 5.

Acceptance criteria
- 24. A module appends events and reads them back, so that each store behaves the same.
- 24.1. GIVEN an empty stream WHEN events are appended and the stream is read THEN the events come back in order, on in-memory, KurrentDB and PostgreSQL.
- 25. Two writers append with the same expected revision, so that only one wins.
- 25.1. GIVEN a stream at revision 2 WHEN one writer appends with expected revision 2 AND another with expected revision 2 THEN one append succeeds AND the other returns a conflict AND appends nothing, on each store.
- 26. A subscriber fails on an event, so that it gets the event again.
- 26.1. GIVEN a subscriber that fails on an event WHEN the next append happens THEN the store delivers the same event to it again before newer events, on each store.
- 26.2. GIVEN a subscriber that fails on an event WHEN the application restarts THEN the subscription gets that event again, on KurrentDB and PostgreSQL.
- 27. A subscriber is added after events exist, so that it catches up.
- 27.1. GIVEN events in the store WHEN a subscriber is added THEN it gets the existing events from the start of the log, on each store.
- 27.2. GIVEN two PostgreSQL transactions that append, where the one with the lower `global_position` commits last WHEN the subscriber polls THEN it gets both events, the one that committed last included.
- 27.3. GIVEN a subscription that handled events WHEN the application restarts THEN it continues after the last event it handled AND doesn't publish the earlier ones again, on KurrentDB and PostgreSQL.
- 27.4. GIVEN a projection that fails to store its checkpoint WHEN the transaction ends THEN its document change is rolled back too.
- 27.5. GIVEN a stored checkpoint already past a batch WHEN the same batch is handled again THEN the checkpoint doesn't change AND the batch's changes are rolled back.

Tests
This step tests 24, 25, 26 and 27.1 on in-memory and KurrentDB, and 27.3-27.5 on KurrentDB with projections on PostgreSQL. 27.2 and the PostgreSQL store are covered in step 7.
- 24.1: appendedEventsComeBackInOrder - shared set.
- 25.1: secondAppendWithTheSameExpectedRevisionConflictsAndAppendsNothing - shared set.
- 26.1: failedEventIsDeliveredAgainBeforeNewerEvents - shared set; the handler fails on its first call; on KurrentDB the second delivery comes after the back-off.
- 26.2: failedEventIsDeliveredAgainAfterARestart - KurrentDB: the handler fails, the store is closed and created again with the same subscription name, and the event comes again.
- 27.1: newSubscriberGetsTheExistingEventsFromTheStart - shared set.
- 27.3: subscriptionContinuesAfterItsCheckpointOnRestart - KurrentDB: a forwarder handles 3 events, the store is closed and created again, one event is appended; asserts the forwarder publishes only the new one.
- 27.4: failedCheckpointRollsBackTheProjectionsDocument - KurrentDB with projections on PostgreSQL; the test adds a PostgreSQL trigger that raises on the checkpoint update for its own `processor_id`, and drops it afterwards; asserts the document is unchanged.
- 27.5: eventAlreadyPastTheCheckpointIsSkippedAndRolledBack - KurrentDB; the test stores a checkpoint past the event's position, then delivers the event; asserts neither the checkpoint nor the document changes.
- extra: inMemoryStoreStartsFromTheBeginningWithoutCheckpoints.
- extra (ported): `RetryBackOffTest` cases.

Existing tests this breaks
- `KurrentDBEventStoreTests` folds into the shared set. `runsMiddlewareForEveryEventBeforeItsSubscribers`, `appendFromInsideASubscriberIsDeliveredAfterTheEventBeingHandled` and `notifiesAllSubscribersOfTheTypeInRegistrationOrder` stay in `InMemoryEventStoreTests`.
```

---

## Step 7. PostgreSQL as the event store

```text
Use the `implement-step` skill.

What to build
`eventstore.type=postgresql` runs the sample on a PostgreSQL event store. Its subscription polls as Emmett does: each event row stores its transaction ID (`pg_current_xact_id()`), the poller reads only rows from transactions older than `pg_snapshot_xmin(pg_current_snapshot())`, ordered by transaction ID and then `global_position`, and continues after the last transaction ID and position it handled. Its checkpoint check compares the transaction ID first, then the position. Its subscription continues the trace from each event's stored `traceId` and `spanId` and restores `correlationId` and `causationId` before the handler runs. It takes a `DataSource` pool with its own connection settings, so it also runs with projections on H2. It passes the shared `EventStore` test set.

Context
- Copy from `workshops/build-your-own-event-store/solved/src/main/java/io/eventdriven/buildyourowneventstore/e04_event_store_methods/postgresql/PostgreSQLEventStore.java` (events table with `stream_position`, `global_position`, JSONB `metadata`; `append_event` locks the stream row; one shared `Connection`; no `subscribe`). The copy implements the sample's `EventStore` and returns KurrentDB client types (`StreamState`, `Position`).
- The checkpoints and the subscription retry from step 6.

Similar code
- Step 6's KurrentDB store and the shared `EventStore` test set.

Blocked by
Steps 4 and 6.

Acceptance criteria
- 24. A module appends events and reads them back, so that each store behaves the same.
- 24.1. GIVEN an empty stream WHEN events are appended and the stream is read THEN the events come back in order, on in-memory, KurrentDB and PostgreSQL.
- 25. Two writers append with the same expected revision, so that only one wins.
- 25.1. GIVEN a stream at revision 2 WHEN one writer appends with expected revision 2 AND another with expected revision 2 THEN one append succeeds AND the other returns a conflict AND appends nothing, on each store.
- 26. A subscriber fails on an event, so that it gets the event again.
- 26.1. GIVEN a subscriber that fails on an event WHEN the next append happens THEN the store delivers the same event to it again before newer events, on each store.
- 26.2. GIVEN a subscriber that fails on an event WHEN the application restarts THEN the subscription gets that event again, on KurrentDB and PostgreSQL.
- 27. A subscriber is added after events exist, so that it catches up.
- 27.1. GIVEN events in the store WHEN a subscriber is added THEN it gets the existing events from the start of the log, on each store.
- 27.2. GIVEN two PostgreSQL transactions that append, where the one with the lower `global_position` commits last WHEN the subscriber polls THEN it gets both events, the one that committed last included.
- 27.3. GIVEN a subscription that handled events WHEN the application restarts THEN it continues after the last event it handled AND doesn't publish the earlier ones again, on KurrentDB and PostgreSQL.
- 27.4. GIVEN a projection that fails to store its checkpoint WHEN the transaction ends THEN its document change is rolled back too.
- 27.5. GIVEN a stored checkpoint already past a batch WHEN the same batch is handled again THEN the checkpoint doesn't change AND the batch's changes are rolled back.
- 28. A module appends an event inside a traced step, so that the metadata is stored with the event.
- 28.1. GIVEN a current span AND a `correlationId` and a `causationId` WHEN an event is appended THEN reading it back gives the same `messageId`, `correlationId`, `causationId`, `traceId` and `spanId`, on each store.
- 28.2. GIVEN an event appended to KurrentDB WHEN its raw metadata is read with the KurrentDB client THEN it has `$correlationId`, `$causationId`, `$traceId` and `$spanId`.

Tests
This step runs 24-27 and 28.1 for PostgreSQL. 28.2 is covered in step 4.
- 24.1, 25.1, 26.1, 27.1, 28.1: the shared `EventStore` test set runs for PostgreSQL.
- 26.2: failedEventIsDeliveredAgainAfterARestart - PostgreSQL.
- 27.2: eventOfATransactionThatCommitsLastIsNotSkipped - two JDBC connections with auto-commit off; connection A appends (takes `global_position` 1) and doesn't commit; connection B appends to another stream and commits; the test triggers one poll and asserts the subscriber got nothing from A; A commits; one more poll; asserts the subscriber got both events.
- 27.3: subscriptionContinuesAfterItsCheckpointOnRestart - PostgreSQL.
- 27.4, 27.5: step 6's checkpoint tests with the PostgreSQL store.
- extra: checkpointComparesTransactionIdBeforePosition - a stored checkpoint (transaction 101, position 5) and a new event (transaction 100, position 7): the event is "past"; a new event (transaction 102, position 3) is not.
- extra: postgreSQLStoreRunsWithProjectionsOnH2 - `eventstore.type=postgresql`, `projections.database=h2`; the application starts and an append succeeds.
- extra: parallelAppendsToTwoStreamsBothSucceed - two threads released by one latch.
- extra: subscriberContinuesTheTraceOfTheStoredEvent - the step span's parent is the stored `spanId`.

Existing tests this breaks
None.
```

---

## Step 8. The bus on Kafka

```text
Use the `implement-step` skill.

What to build
`messaging.bus=kafka` runs the bus on Kafka. Each declared channel is a topic; each queue bound with `#` is a consumer group on the channel topic; each queue with a narrower pattern gets its own topic, filled by the routing consumer. The record key is the route's routing key. Every channel and queue topic is declared at start with 3 partitions and 1 replica, and Kafka's automatic topic creation is off. Consumers start from `earliest`. Messages are serialized with stable type names from one broker-neutral versioning package, trusting only the sample's package. The five values travel in the headers, `messageId` from step 4's rules; `correlationId` and `causationId` travel as baggage. The consumer opens the step span and restores the five values before the handler runs. The sample's `docker-compose.yml` gets Kafka (`confluentinc/confluent-local:8.4.1`, automatic topic creation off) in the `sample` and `all` profiles, and `TestDatabases` finds it or starts one shared Testcontainers Kafka.

Context
- `M:core/messaging/MessageBus.java`, `InMemoryMessageBus.java` (its pattern matching rules are reused by the routing consumer), `MessageRouter.java`, `MessageRoutes.java`.
- `M:ecommerce/ECommerceApplicationConfig.java` chooses the bus from `messaging.bus`; `kafka` joins the allowed values.
- Workshop code to copy, with the review's fixes: `W:sagas_kafka/core/KafkaMessageBus.java`, `KafkaConfiguration.java` (topics, listener containers), `KafkaMessageBusProperties.java`, `KafkaMessageBaggageHandler.java`, `messages/MessageMetadata.java` (without its random `messageId`), `VersionedMessageConverter.java`, `versioning/*` (one copy, review N8), the workshop's `application.properties`.
- Review fixes here: U1, U2, U3, N3, N4 (`broker-setup-review/findings.md` and its "Mapping the target bus onto the brokers").
- Dynamic listener containers per queue: `factory.createContainer(topic)`, `setGroupId`, `start()`; they stop when the context closes.
- The routing consumer takes the `KafkaTemplate` it sends with, so a test can give it one whose first send fails.

Similar code
- `W:sagas_kafka/core/KafkaMessageRoutingTest.java`, `KafkaMessageMetadataTest.java`, `KafkaMessageBaggageHandlerTest.java`, `messages/MessageMetadataTest.java`, `VersionedMessageConverterTest.java`, `KafkaTestConfiguration.java`.
- `T:core/messaging/InMemoryMessageBusTests.java` becomes the shared `MessageBus` test set.

Blocked by
Steps 1 and 4.

Acceptance criteria
- 6. A module publishes a command to a channel, so that the queue bound to the channel handles it.
- 6.1. GIVEN a queue bound to `payments-requests` with `#` AND a subscriber for `AuthorizePayment` WHEN the saga publishes `AuthorizePayment` THEN the subscriber handles it once, on each broker.
- 7. A module publishes a response with a routing key, so that only the requester whose pattern matches gets it.
- 7.1. GIVEN `orders-payments-responses` bound to `payments-responses` with `ecommerce.order.*` AND another queue bound with `ecommerce.invoice.*` WHEN payments publishes `PaymentAuthorized` with the key `ecommerce.order.42-1` THEN only `orders-payments-responses` gets it, on each broker.
- 8. A module publishes a message of a type that the queue has no subscriber for, so that nothing breaks.
- 8.1. GIVEN a queue bound to a channel AND no subscriber for `PaymentFailed` in that queue WHEN `PaymentFailed` reaches the queue THEN no handler runs AND the next message in the queue is handled.
- 9. A forwarder publishes an external event that no queue is bound to, so that the forwarder doesn't stop.
- 9.1. GIVEN no queue is bound to `shopping_carts-external` WHEN the cart forwarder publishes `ShoppingCartCheckedOut` THEN the publish succeeds AND the forwarder's next event is published.
- 9.2. GIVEN RabbitMQ AND no queue is bound to `payments-requests` WHEN the saga publishes `AuthorizePayment` THEN the publish fails, because `requests` channels publish with `mandatory`.
- 10. A module publishes to a channel that no route or topology declares, so that a wrong channel name is found at once.
- 10.1. GIVEN Kafka with automatic topic creation switched off WHEN a message is published to a channel that is not declared THEN the publish fails AND no topic is created.
- 12. A new queue joins a channel that already has messages, so that it doesn't skip them.
- 12.1. GIVEN messages already in a Kafka channel topic AND a new consumer group for a queue WHEN the queue's consumer starts THEN it reads the channel from the earliest offset.
- 14. The Kafka routing consumer forwards a response, so that the requester gets it with all its data.
- 14.1. GIVEN a response on `payments-responses` with a key, a type, a trace and the five metadata values WHEN the routing consumer writes it to `orders-payments-responses` THEN the copy has the same key, type, headers and body.
- 14.2. GIVEN the routing consumer fails after it reads a response and before it writes it WHEN it restarts THEN it writes the response, because it commits the offset only after the write.
- 15. Every message type has an explicit route with a channel and a key, so that no message is dropped and Kafka keeps one entity's messages in order.
- 15.1. GIVEN two messages about the same payment WHEN they are published to a Kafka channel THEN both have the payment's routing key as the record key AND reach the same partition.
- 15.2. GIVEN the routes of the four modules WHEN each message type that the modules publish through the bus is routed THEN it gets a channel AND a routing key.
- 15.3. GIVEN a message type with no route WHEN it is published THEN the publish fails with an error that names the message type, on in-memory, Kafka and RabbitMQ.
- 22. A consumer reads a message under its stable name, so that renaming a Java class doesn't break the contract.
- 22.1. GIVEN a message published with its stable type name WHEN the consumer reads it THEN the consumer maps the name to the current class.
- 35. An event is delivered twice, so that its copies are the same message.
- 35.1. GIVEN an event delivered again after a failure WHEN the forwarder publishes it again THEN both copies have the same `messageId`.
- 35.2. GIVEN the saga handles `OrderPlaced` AND publishes `AuthorizePayment` and `ReserveStock` WHEN `OrderPlaced` is delivered again THEN the saga publishes them again with the same two `messageId` values AND the two values differ from each other.

Tests
This step tests these criteria on in-memory and Kafka. 9.2 is covered in step 10 (RabbitMQ); 15.2 in step 1.
- 6.1: commandReachesTheQueueBoundToItsChannelOnce - shared set.
- 7.1: responseReachesOnlyTheQueueWhosePatternMatches - shared set; on Kafka through the routing consumer.
- 8.1: messageWithNoSubscriberInTheQueueIsSkipped - shared set.
- 9.1: publishToAChannelWithNoQueueSucceedsAndTheForwarderGoesOn - shared set; the cart forwarder publishes two checkouts; asserts both publishes succeed.
- 10.1: publishToAnUndeclaredChannelFailsAndCreatesNoTopic - Kafka; asserts the publish fails and the admin client lists no such topic.
- 12.1: newConsumerGroupReadsFromTheEarliestOffset - publish, then bind and start a new queue; asserts it gets the earlier message.
- 14.1: routingConsumerKeepsKeyTypeHeadersAndBody.
- 14.2: routingConsumerWritesAResponseAfterAFailedSendBeforeTheCommit - the routing consumer gets a `KafkaTemplate` whose first send fails; asserts the response reaches `orders-payments-responses` once the container retries the record.
- 15.1: twoMessagesAboutOnePaymentShareTheRecordKeyAndPartition.
- 15.3: publishingAMessageWithoutARouteFailsNamingItsType - shared set.
- 22.1: consumerMapsTheStableTypeNameToTheCurrentClass.
- 35.1: forwarderRepublishesARedeliveredEventWithTheSameMessageId - Kafka; reads both records' `messageId` headers.
- 35.2: sagaRepublishesBothCommandsWithTheSameIdsOnRedelivery - Kafka; reads the record headers.
- extra (ported): the `KafkaMessageMetadataTest`, `KafkaMessageBaggageHandlerTest` and `MessageMetadataTest` cases: the five values are in the record headers, and the handled `messageId` becomes the `causationId`.
- extra: consumerOpensAStepSpanNamedAfterTheMessageType.

Existing tests this breaks
- `InMemoryMessageBusTests` becomes the shared set. `deliversDepthFirstSoNestedPublishCompletesBeforeSubscriberReturns`, `runsMiddlewareForEveryMessageBeforeSubscribersInRegistrationOrder` and `deliversMessagesInTheOrderTheyWereGiven` stay in-memory only.
```

---

## Step 9. Failures and versioning on Kafka

```text
Use the `implement-step` skill.

What to build
On Kafka, a failing handler is retried with back-off, and a message that still fails, can't be read, or names an untrusted type goes to its queue's `<queue>-dlt`, which is declared at start with the queue topic. The `messaging.dead_lettered` counter counts it. The dead-letter template is a Spring bean with the observation registry, so the dead-letter publish has a span. When Kafka is down, a publish fails within the publish timeout, because the producer's `delivery.timeout.ms` and `max.block.ms` fit inside it, and the forwarder's checkpoint doesn't move. Old message versions are transformed on read.

Context
- Workshop code to copy: `W:core/MessageFailureStrategy.java`, `W:core/UnreadableMessageException.java`, the error handler and `DeadLetterPublishingRecoverer` in `W:sagas_kafka/core/KafkaConfiguration.java:53-104`, `versioning/MessageTransformations.java`, `MessageTransformer.java`. `RetryBackOff` is from step 6.
- Review fixes: N1, N2, N5; findings F4, F5.
- The checkpoints and the forwarder subscription from step 6.

Similar code
- `W:sagas_kafka/core/KafkaErrorHandlingTest.java`, `KafkaPublishFailureTest.java`, `W:core/MessageFailureStrategyTest.java`, `VersionedMessageConverterTest.java`.

Blocked by
Steps 6 and 8.

Acceptance criteria
- 11. The broker is not reachable, so that the outbox keeps the message.
- 11.1. GIVEN the broker is down WHEN a forwarder publishes an external event THEN the publish fails within the publish timeout AND the forwarder's checkpoint in the projections database doesn't move AND the event is published again after the broker is back.
- 16. A handler fails once, so that the message is handled on a later attempt.
- 16.1. GIVEN a handler that fails on the first attempt and succeeds on the second WHEN its queue gets a message THEN the handler succeeds on the second attempt AND nothing goes to a dead-letter destination, on each broker.
- 17. A handler fails on every attempt, so that the message is kept for a person to look at.
- 17.1. GIVEN a handler that always fails WHEN its queue gets a message THEN the message goes to the dead-letter destination of that queue after the last retry AND `messaging.dead_lettered` increases by one AND the other queues on the same channel don't get a copy.
- 17.2. GIVEN a handler that always fails on Kafka WHEN the message goes to the dead-letter topic THEN the dead-letter publish has a span in the message's trace.
- 18. A queue gets a message it can't read, so that the queue doesn't block.
- 18.1. GIVEN a message whose body is not valid JSON WHEN it reaches the queue THEN it goes to the queue's dead-letter destination without retries AND the next message is handled.
- 19. A queue gets a message whose type name is unknown or outside the sample's package, so that no unexpected class is created.
- 19.1. GIVEN a message whose type header names a class outside the sample's package WHEN it reaches the queue THEN no instance of that class is created AND the message goes to the dead-letter destination.
- 23. A consumer reads an old version of a message, so that a producer can evolve the message.
- 23.1. GIVEN a message published in an old version AND a transformation registered for it WHEN the consumer reads it THEN the handler gets the current version.

Tests
This step tests these criteria on Kafka. Step 11 covers 16-19 and 23 on RabbitMQ.
- 11.1: forwarderPublishFailsWithinTheTimeoutAndKeepsItsCheckpointWhileKafkaIsDown - own server: a Kafka container for this class, the KurrentDB store, projections on H2; `docker pause` the Kafka container, append an event, assert the forwarder's publish fails within the publish timeout plus one second and its checkpoint is unchanged; `docker unpause`; assert the event reaches the channel topic.
- 16.1: handlerFailingOnceSucceedsOnTheSecondAttempt.
- 17.1: alwaysFailingHandlerSendsTheMessageToItsQueuesDeadLetterTopic - asserts `<queue>-dlt`, `messaging.dead_lettered` +1, and nothing in the other queue's dead-letter topic.
- 17.2: deadLetterPublishHasASpanInTheMessagesTrace.
- 18.1: invalidJsonGoesToTheDeadLetterTopicWithoutRetries.
- 19.1: typeOutsideTheSamplesPackageIsNotCreatedAndIsDeadLettered.
- 23.1: oldVersionIsTransformedToTheCurrentOne.
- extra (ported): `MessageFailureStrategyTest` cases.

Existing tests this breaks
None.
```

---

## Step 10. The bus on RabbitMQ

```text
Use the `implement-step` skill.

What to build
`messaging.bus=rabbitmq` runs the bus on RabbitMQ. Each declared channel is a durable topic exchange, declared also when no queue is bound; a publish to an undeclared channel fails at once, naming the channel. Each queue is a durable quorum queue; `bind` is a binding with the same pattern, redeclared by the `RabbitAdmin` after a reconnect (`setRedeclareManualDeclarations(true)`) so it survives a lost connection. One listener container per queue dispatches by type. Publishing waits for the publisher confirm; `mandatory` applies to `requests` and `internal` channels only. The five values travel in the headers, `messageId` as the AMQP `message-id`; `correlationId` and `causationId` travel as baggage; publishers use their own connection; the consumer opens the step span and restores the five values before the handler runs. The sample's `docker-compose.yml` gets RabbitMQ (`rabbitmq:4.4.0-management`) in the `rabbitmq` and `all` profiles, and `TestDatabases` finds it or starts one shared Testcontainers RabbitMQ.

Context
- The bus seam, the shared `MessageBus` test set and the versioning package from steps 8 and 9.
- Workshop code to copy: `W:sagas_rabbitmq/core/RabbitMQMessageBus.java`, `RabbitConfig.java`, `RabbitMQMessageBusProperties.java`, `RabbitMessageBaggageHandler.java`, the converter.
- Review fixes: N6, the mapping section (`setRedeclareManualDeclarations`, `RabbitListenerConfigurer`).
- `M:ecommerce/ECommerceApplicationConfig.java`: `rabbitmq` joins the allowed values.

Similar code
- `W:sagas_rabbitmq/core/RabbitMQMessageRoutingTest.java`, `RabbitMQMessageMetadataTest.java`, `RabbitMessageBaggageHandlerTest.java`, `RabbitMQPublishTest.java`, `RabbitMQTestConfiguration.java`.

Blocked by
Step 9.

Acceptance criteria
- 6. A module publishes a command to a channel, so that the queue bound to the channel handles it.
- 6.1. GIVEN a queue bound to `payments-requests` with `#` AND a subscriber for `AuthorizePayment` WHEN the saga publishes `AuthorizePayment` THEN the subscriber handles it once, on each broker.
- 7. A module publishes a response with a routing key, so that only the requester whose pattern matches gets it.
- 7.1. GIVEN `orders-payments-responses` bound to `payments-responses` with `ecommerce.order.*` AND another queue bound with `ecommerce.invoice.*` WHEN payments publishes `PaymentAuthorized` with the key `ecommerce.order.42-1` THEN only `orders-payments-responses` gets it, on each broker.
- 8. A module publishes a message of a type that the queue has no subscriber for, so that nothing breaks.
- 8.1. GIVEN a queue bound to a channel AND no subscriber for `PaymentFailed` in that queue WHEN `PaymentFailed` reaches the queue THEN no handler runs AND the next message in the queue is handled.
- 9. A forwarder publishes an external event that no queue is bound to, so that the forwarder doesn't stop.
- 9.1. GIVEN no queue is bound to `shopping_carts-external` WHEN the cart forwarder publishes `ShoppingCartCheckedOut` THEN the publish succeeds AND the forwarder's next event is published.
- 9.2. GIVEN RabbitMQ AND no queue is bound to `payments-requests` WHEN the saga publishes `AuthorizePayment` THEN the publish fails, because `requests` channels publish with `mandatory`.
- 13. The application restarts, so that the queues keep their bindings and messages.
- 13.1. GIVEN messages waiting in a RabbitMQ queue WHEN the application restarts THEN the exchanges, queues and bindings still exist AND the waiting messages are handled.
- 13.2. GIVEN the RabbitMQ connection is lost after the start WHEN it comes back THEN the bindings made by `bind` exist again.
- 15. Every message type has an explicit route with a channel and a key, so that no message is dropped and Kafka keeps one entity's messages in order.
- 15.1. GIVEN two messages about the same payment WHEN they are published to a Kafka channel THEN both have the payment's routing key as the record key AND reach the same partition.
- 15.2. GIVEN the routes of the four modules WHEN each message type that the modules publish through the bus is routed THEN it gets a channel AND a routing key.
- 15.3. GIVEN a message type with no route WHEN it is published THEN the publish fails with an error that names the message type, on in-memory, Kafka and RabbitMQ.
- 22. A consumer reads a message under its stable name, so that renaming a Java class doesn't break the contract.
- 22.1. GIVEN a message published with its stable type name WHEN the consumer reads it THEN the consumer maps the name to the current class.
- 35. An event is delivered twice, so that its copies are the same message.
- 35.1. GIVEN an event delivered again after a failure WHEN the forwarder publishes it again THEN both copies have the same `messageId`.
- 35.2. GIVEN the saga handles `OrderPlaced` AND publishes `AuthorizePayment` and `ReserveStock` WHEN `OrderPlaced` is delivered again THEN the saga publishes them again with the same two `messageId` values AND the two values differ from each other.

Tests
This step runs the shared `MessageBus` test set for RabbitMQ and completes 6-9, 15.3, 22 and 35. 15.1 is covered in step 8, 15.2 in step 1.
- 6.1, 7.1, 8.1, 9.1, 15.3, 22.1: shared set on RabbitMQ.
- 9.2: publishToAChannelOfRequestsWithNoQueueFails.
- 13.1: waitingMessagesAreHandledAfterARestart - publish with the consumers stopped, close the context, start a new one; asserts the messages are handled.
- 13.2: bindingsExistAgainAfterTheConnectionComesBack - close the connection through the management API; asserts the bindings through the management API.
- 35.1, 35.2: Kafka tests of step 8, on RabbitMQ, reading the AMQP `message-id`.
- extra: publishToAnUndeclaredChannelFailsNamingTheChannel.
- extra (ported): `RabbitMQMessageMetadataTest`, `RabbitMessageBaggageHandlerTest`, `RabbitMQPublishTest` cases.

Existing tests this breaks
None.
```

---

## Step 11. Failures and versioning on RabbitMQ

```text
Use the `implement-step` skill.

What to build
On RabbitMQ, a failing handler is retried with back-off, and a message that still fails, can't be read, or names an untrusted type goes to its queue's own dead-letter queue through a direct dead-letter exchange. The queues get at-least-once dead-lettering, reject-publish overflow and the default delivery limit, so a dead letter stays in the source queue until the dead-letter queue confirms it. `messaging.dead_lettered` counts it. Publishers use their own connection (built in step 10); this step proves that a blocked publisher doesn't stop the consumers. Old message versions are transformed on read.

Context
- Workshop code: the retry customizer and recoverer in `W:sagas_rabbitmq/core/RabbitConfig.java:67-133`; `MessageFailureStrategy` from step 9.
- Review fixes: U4, N5, N7.
- Step 10 declared the queues without dead-letter arguments. Changing the arguments of an existing queue fails with `PRECONDITION_FAILED`, so this step changes the queue declarations before any Compose RabbitMQ keeps them: the HOWTO (step 16) tells developers to remove old queues once (`docker compose down -v`).

Similar code
- `W:sagas_rabbitmq/core/RabbitMQErrorHandlingTest.java`; step 9's Kafka tests.

Blocked by
Step 10.

Acceptance criteria
- 16. A handler fails once, so that the message is handled on a later attempt.
- 16.1. GIVEN a handler that fails on the first attempt and succeeds on the second WHEN its queue gets a message THEN the handler succeeds on the second attempt AND nothing goes to a dead-letter destination, on each broker.
- 17. A handler fails on every attempt, so that the message is kept for a person to look at.
- 17.1. GIVEN a handler that always fails WHEN its queue gets a message THEN the message goes to the dead-letter destination of that queue after the last retry AND `messaging.dead_lettered` increases by one AND the other queues on the same channel don't get a copy.
- 17.2. GIVEN a handler that always fails on Kafka WHEN the message goes to the dead-letter topic THEN the dead-letter publish has a span in the message's trace.
- 18. A queue gets a message it can't read, so that the queue doesn't block.
- 18.1. GIVEN a message whose body is not valid JSON WHEN it reaches the queue THEN it goes to the queue's dead-letter destination without retries AND the next message is handled.
- 19. A queue gets a message whose type name is unknown or outside the sample's package, so that no unexpected class is created.
- 19.1. GIVEN a message whose type header names a class outside the sample's package WHEN it reaches the queue THEN no instance of that class is created AND the message goes to the dead-letter destination.
- 20. RabbitMQ can't take a dead letter, so that it is not lost.
- 20.1. GIVEN a quorum queue with at-least-once dead-lettering AND the dead-letter queue can't take the message WHEN a message is dead-lettered THEN it stays in the source queue until the dead-letter queue confirms it.
- 21. A publisher is blocked by a RabbitMQ resource alarm, so that consumers keep working.
- 21.1. GIVEN publishing is blocked WHEN a queue has messages THEN its consumers still receive them, because publishers use their own connection.
- 23. A consumer reads an old version of a message, so that a producer can evolve the message.
- 23.1. GIVEN a message published in an old version AND a transformation registered for it WHEN the consumer reads it THEN the handler gets the current version.

Tests
This step completes 16-19 and 23 on RabbitMQ. 17.2 is covered in step 9 (the Kafka dead-letter template).
- 16.1, 17.1, 18.1, 19.1, 23.1: RabbitMQ versions of step 9's tests.
- 20.1: deadLetterStaysInTheSourceQueueWhileTheDeadLetterQueueCantTakeIt - own server with `dead_letter_worker_publisher_confirm_timeout` set to 5000 ms; the test sets a policy `max-length=0` with `overflow=reject-publish` on the dead-letter queue through the management API; a handler always fails on two messages; a quorum queue with `max-length=0` still accepts the first dead letter (measured in step 11), so the test asserts one message reaches the dead-letter queue and the other stays in the source queue; it removes the policy and asserts both are in the dead-letter queue.
- 21.1: consumersKeepReceivingWhilePublishingIsBlocked - own server; publish one message, set `rabbitmqctl set_vm_memory_high_watermark 0` in the container, publish through the bus (it blocks), assert the consumer still receives the first message; reset the watermark in a `finally`.

Existing tests this breaks
None.
```

---

## Step 11a. A stable event catalog check

```text
Use the `implement-step` skill.

What to build
The event catalog's browser check for a module page waits until the page's sidebar has rendered before it reads the "Outbound Messages" and "Inbound Messages" lists, so it doesn't fail when the machine is slow after the Java test run. Oskar chose this fix (Q32).

Context
- `eventcatalog/tests/catalog.spec.js:153` "<Module> page shows what the module sends and receives": `sidebarSection(page, 'Outbound Messages')` reads `getByRole('button', { name: 'Outbound Messages', exact: true }).locator('..').locator(':scope > div > button[title]')` with Playwright's default 5 s wait. In full `./gradlew build eventCatalogTest -Pci=true` runs it failed three times with "element(s) not found" on the Orders page, and passed when run alone (`progress.md`, coordinator notes).
- `eventcatalog/playwright.config.js` (`fullyParallel: true`, `npm run preview` web server).

Similar code
- The other checks in `catalog.spec.js` that wait with `await expect(...).toBeVisible()` or `toHaveText`.

Blocked by
None.

Acceptance criteria
None. The step protects the existing catalog checks; it changes no spec behaviour.

Tests
- extra: modulePageListsWhatItSendsAndReceivesOnceTheSidebarHasRendered - the existing check waits with a web-first assertion (for example `await expect(section).not.toHaveCount(0)` or `toHaveText` on the list) instead of reading the list at once; no longer timeout and no `waitForTimeout`.
- extra: the full `./gradlew build eventCatalogTest -Pci=true` passes three runs in a row.

Existing tests this breaks
None.
```

---

## Step 12. One purchase on Kafka in one trace

```text
Use the `implement-step` skill.

What to build
A purchase on Kafka, KurrentDB and PostgreSQL forms one trace of step spans. Each step has the purchase's `correlationId` and the `messageId` of its parent as `causationId`. A store subscription continues the trace from the event: the in-memory and KurrentDB subscriptions open the step span with the stored `traceId` and `spanId` as parent, as step 7's PostgreSQL subscription already does (`MessageContext.handling`). The commands that arrive with no trace context from the purchase continue the trace from the last event of the stream they change: the webhook commands (`ConfirmPaymentAuthorization`, `ConfirmPaymentCapture`, `FailPaymentCapture`), the fulfillment service's answers (`ConfirmPackageSent`, `RejectFulfillment`, `DeliverPackage`) and the worker commands (`CloseUnpaidOrder`, `ExpirePaymentAuthorization`, `TimeOutPayment`). `CheckOutShoppingCart` starts the purchase: it doesn't continue from the cart's earlier events. "Pay now" and "Retry payment" stay plain facade calls and start a new flow (spec r21, Q33).

Context
- The five values and `read` metadata from step 4; Kafka from steps 8-9; KurrentDB from step 6; projections on PostgreSQL from step 3.
- Publishers outside a purchase: `M:ecommerce/payments/sandbox/SandboxPaymentGateway.java:77,92`, `M:ecommerce/shipments/sandbox/SandboxFulfillmentService.java:26`, `UnpaidOrdersWorker`, `AuthorizationExpiryWorker`, `PaymentTimeoutWorker`. Their message gets a new `correlationId`; the handler restores the purchase's.
- `TT:ApiSpecification.java`, `T:ecommerce/api/builders/PurchaseRestBuilder.java`. All tests of this step run in one test class on the Kafka, KurrentDB and PostgreSQL settings, through `ApiSpecification`, and read the spans from the in-memory span exporter.

Similar code
- `W:sagas_kafka/core/MessageMetadataPropagationTest.java`; `T:ecommerce/api/PurchaseOverHttpTests.java`.

Blocked by
Steps 3, 6 and 9.

Acceptance criteria
- 30. A customer checks out, so that the purchase starts one trace.
- 30.1. GIVEN a cart with products WHEN the customer checks out THEN `CheckOutShoppingCart` starts the trace AND every message and event that follows carries the same `correlationId`.
- 31. A handler publishes the next message, so that the business parent step is known.
- 31.1. GIVEN the saga handles `ShoppingCartCheckedOut` with `messageId` X WHEN it publishes `PlaceOrder` THEN `PlaceOrder` has `causationId` X AND the same `correlationId` AND the same `traceId`.
- 32. A subscriber of the event store publishes, so that the trace crosses the store.
- 32.1. GIVEN an event stored inside a span WHEN the forwarder publishes the external event for it THEN the publish span is in the same trace AND the external event's `causationId` is the stored event's `messageId`.
- 33. A step after a wait starts outside the purchase, from the payment provider's webhook, so that the trace continues.
- 33.1. GIVEN a purchase waiting for the customer to pay WHEN `ConfirmPaymentAuthorization` arrives with no trace context THEN the step that handles it is in the purchase's trace AND has the purchase's `correlationId`.
- 34. The fulfillment service answers after a wait, so that the trace continues.
- 34.1. GIVEN a shipment waiting for the warehouse WHEN `ConfirmPackageSent` arrives with no trace context THEN the step that handles it is in the purchase's trace AND has the purchase's `correlationId`.
- 36. A developer runs one purchase on Kafka, so that the whole flow is proven on the broker.
- 36.1. GIVEN Kafka, KurrentDB and PostgreSQL WHEN a customer checks out, pays and the package is sent THEN the order completes AND the step spans from the checkout to "Pay now" form one trace with one `correlationId`, AND the step spans from "Pay now" to the package sent form a second trace with a second `correlationId`, AND each step has the `messageId` of its parent as `causationId`.

Tests
This step tests 30-34 and 36 on Kafka.
- 30.1: checkoutStartsTheTraceAndEveryFollowingMessageCarriesItsCorrelationId - a cart with two added products is checked out; asserts the checkout's step span has no parent from the add-product requests, and every later step has its `correlationId`.
- 31.1: placeOrderHasTheCheckedOutMessageAsItsCause.
- 32.1: forwarderPublishSpanIsInTheStoredEventsTrace.
- 33.1: confirmPaymentAuthorizationWithNoTraceContextContinuesThePurchasesTrace - the test publishes the command from a thread with no span and no baggage.
- 34.1: confirmPackageSentWithNoTraceContextContinuesThePurchasesTrace - the same, not through the sandbox.
- 36.1: purchaseOnKafkaFormsTwoTracesSplitAtPayNow - asserts the order completes, the step spans before "Pay now" share one trace ID and one `correlationId`, the ones from "Pay now" on share a second trace ID and `correlationId`, and each step's `causationId` is its parent's `messageId`.
- extra: kurrentDBSubscriberContinuesTheTraceOfTheStoredEvent - the step span's parent is the stored `spanId`; the same on in-memory.
- extra: workerCommandContinuesThePurchasesTrace - `CloseUnpaidOrder` published by the worker; asserts the events it causes carry the purchase's `correlationId`.

Existing tests this breaks
None. The API tests stay on the `in-memory` profile.
```

---

## Step 13. One purchase on RabbitMQ in one trace

```text
Use the `implement-step` skill.

What to build
The same purchase runs on RabbitMQ with PostgreSQL as the event store and for projections, and forms one trace with the rules of step 12. The trace crosses the RabbitMQ consumer (step 10) and the PostgreSQL subscription (step 7); this step fixes what the purchase test shows missing there.

Context
- Steps 7, 10, 11, 12.

Similar code
- Step 12's purchase test; `W:sagas_rabbitmq/core/MessageMetadataPropagationTest.java`.

Blocked by
Steps 7, 11 and 12.

Acceptance criteria
- 37. A developer runs one purchase on RabbitMQ, so that the other broker is proven too.
- 37.1. GIVEN RabbitMQ, PostgreSQL as the event store and PostgreSQL for projections WHEN a customer checks out, pays and the package is sent THEN the order completes AND the finished spans form one trace with the same rules as 36.1.

Tests
- 37.1: purchaseOnRabbitMQAndPostgreSQLFormsTwoTracesSplitAtPayNow - the step 12 assertions on these settings.

Existing tests this breaks
None.
```

---

## Step 14. Traces, logs and metrics in Grafana

```text
Use the `implement-step` skill.

What to build
Traces, metrics and logs go to Grafana LGTM over OTLP. Log4j2 sends logs through OpenTelemetry's Log4j2 appender, with the `traceId` and `spanId` on each line. `KurrentDBEventStore.append` wraps `appendToStream` in a Micrometer `Observation` named `streams.append` (`db.system=kurrentdb`, the stream name), so each KurrentDB write shows as a span inside its step; the global OpenTelemetry stays unregistered (Q34). The sample's `docker-compose.yml` gets `grafana/otel-lgtm:0.41.0` with the workshop's Tempo config in the `sample` and `all` profiles, with a provisioned dashboard built only from existing metrics: the bus's listener and template timers with their error tag (published, handled and failed per channel), `messaging.dead_lettered`, and `http.server.requests`; each panel links to traces. Without an OTLP endpoint, nothing logs connection errors.

Context
- `src/main/resources/log4j2.xml`, `src/test/resources/log4j2-test.xml`, `application.properties`.
- Workshop: `workshops/event-driven-architecture/docker-compose.yml` (LGTM), `workshops/event-driven-architecture/observability/tempo-config.yaml`, the workshop's `application.properties` (OTLP, silenced exporter logs).
- `kurrentdb-client-spans/findings.md` section 4 A: why the client's own span can't be used, and the Observation shape. `M:core/kurrentdb/KurrentDBEventStore.java` has two `append` overloads (lines 49 and 71) that share the wrapped call; the store takes an `ObservationRegistry`.

Similar code
- The workshop's e03 README "Seeing it" section.

Blocked by
Steps 9, 11 and 12.

Acceptance criteria
- 38. A developer opens Grafana after a purchase, so that they see the whole purchase.
- 38.1. GIVEN a completed purchase WHEN the developer searches Tempo by the `correlationId` of the purchase's first flow (checkout to "Pay now") THEN Tempo shows that flow's trace with one span per step, AND each KurrentDB append as a `streams.append` span inside its step.
- 39. A developer follows a span to its logs, so that they see why a step failed.
- 39.1. GIVEN a span of a failed handler WHEN the developer opens its logs THEN Loki shows the log lines with that span's `traceId`.
- 40. A developer opens the dashboard, so that they see the purchase flow at a glance.
- 40.1. GIVEN Grafana LGTM started from the sample's Docker Compose WHEN the developer opens the dashboard THEN it shows messages published, handled and failed per channel, dead letters and HTTP requests AND each panel links to the traces.
- 41. A test checks the export, so that a broken export is found in the build.
- 41.1. GIVEN Grafana LGTM in Testcontainers WHEN a purchase runs THEN Tempo has its trace AND Loki has its logs AND Prometheus has the bus's listener and template timers and `http_server_requests` AND Grafana has loaded the dashboard.
- 42. Grafana LGTM doesn't run, so that the application and the tests still work.
- 42.1. GIVEN no OTLP endpoint WHEN the application runs or the tests run THEN they pass AND the exporters log no connection errors.

Tests
All tests of this step run the default setup (Kafka, KurrentDB, PostgreSQL) against `LgtmStackContainer`, except 42.1.
- 38.1: tempoFindsTheFirstFlowByCorrelationIdWithItsStepAndAppendSpans - Tempo search API; asserts each `streams.append` span's parent is a step span of the flow.
- extra: aFailedAppendMarksItsStreamsAppendSpanAsAnError - a version conflict on KurrentDB; asserts the span has an error.
- 39.1: lokiHasTheFailedHandlersLogLinesWithItsTraceId.
- 40.1: dashboardHasThePanelsAndTraceLinks - Grafana's dashboard HTTP API; asserts the panels and their trace links.
- 41.1: purchaseIsExportedToTempoLokiAndPrometheus - asserts the trace, the logs, the listener and template timers and `http_server_requests`, and the loaded dashboard.
- 42.1: noOtlpEndpointLogsNoConnectionErrors - OTLP endpoint on a closed local port, metric export step 1 second, span export forced with a flush; captures the log output for 5 seconds after a purchase; asserts it has no exporter errors.

Existing tests this breaks
None.
```

---

## Step 15. Real infrastructure by default

```text
Use the `implement-step` skill.

What to build
`./gradlew bootRun` with no config starts the sample's Docker Compose services through Spring Boot's Docker Compose support and runs on Kafka, KurrentDB and PostgreSQL. The defaults switch to `messaging.bus=kafka`, `eventstore.type=kurrentdb`, `projections.database=postgresql`. The application sets `spring.docker.compose.profiles.active=sample,${messaging.bus}`, so RabbitMQ starts only when the bus is RabbitMQ. The `in-memory` profile switches Docker Compose off.

Context
- `docker-compose.yml` (every service has `profiles:`; KurrentDB, PostgreSQL, Kafka and LGTM are in `sample`, RabbitMQ in `rabbitmq`).
- `build.gradle` (`spring-boot-docker-compose` as a development-only dependency; `openApi` `customBootRun` already uses the `in-memory` profile from step 2).
- `.github/workflows/samples_distributed-processes.yml` (`./gradlew build -Pci=true`, `eventCatalogTest`, git status check).

Similar code
- Step 2's profile and its tests.

Blocked by
Steps 7, 11 and 14.

Acceptance criteria
- 1. A developer runs the application with no config, so that they see the purchase on real infrastructure.
- 1.1. GIVEN Docker runs AND the sample's Docker Compose services don't run WHEN the developer runs `./gradlew bootRun` THEN Spring Boot starts Kafka, KurrentDB, PostgreSQL and Grafana LGTM AND doesn't start RabbitMQ AND the application uses Kafka, KurrentDB and PostgreSQL.
- 1.2. GIVEN the sample's Docker Compose services already run WHEN the developer runs `./gradlew bootRun` THEN the application uses the running services AND starts no second copy.
- 2. A developer selects the in-memory setting, so that they can run the sample without Docker.
- 2.1. GIVEN Docker doesn't run WHEN the developer runs `./gradlew bootRun` with the in-memory setting THEN the application starts AND uses the in-memory bus, the in-memory event store and H2 AND starts no Docker Compose service.
- 3. A developer selects RabbitMQ as the bus, so that they see the same purchase on the other broker.
- 3.1. GIVEN Docker runs WHEN the developer starts the application with the bus set to RabbitMQ THEN Spring Boot also starts RabbitMQ AND the customer can buy, pay and receive the order as with Kafka.
- 4. A developer selects PostgreSQL as the event store, so that they see the same purchase on the other store.
- 4.1. GIVEN the Docker Compose services run WHEN the developer starts the application with the event store set to PostgreSQL THEN the customer can buy, pay and receive the order as with KurrentDB.
- 45. CI runs the folder's workflow, so that every implementation is proven.
- 45.1. GIVEN no Docker Compose services in CI WHEN `./gradlew build` and `./gradlew eventCatalogTest` run THEN the shared tests start their servers with Testcontainers AND pass AND the generated AsyncAPI documents and the catalog match the code.

Tests
- 1.1, 1.2: no automated test (Q28). The HOWTO (step 16) describes the check.
- 2.1: the API tests and the `ecommerce/web` tests run with the `in-memory` profile and no Docker; extra: inMemoryProfileStartsWithoutDockerCompose - a context with the profile starts while the Docker Compose support has no services to start.
- 3.1, 4.1: covered by step 13's purchase test on RabbitMQ and the PostgreSQL event store (Q28).
- 45.1: CI runs `./gradlew build` and `./gradlew eventCatalogTest`; the generated AsyncAPI documents and the catalog match the code (the workflow's git status check).
- extra: withNoSettingsTheApplicationUsesKafkaKurrentDBAndPostgreSQL - a context with no profile and the Testcontainers servers; asserts the bus is Kafka, the store `KurrentDBEventStore` and the data source PostgreSQL.
- extra: rabbitMQBusAddsTheRabbitMQComposeProfile - with `messaging.bus=rabbitmq`, the resolved Compose profiles are `sample` and `rabbitmq`.

Existing tests this breaks
None; the Spring tests got the `in-memory` profile in step 2.
```

---

## Step 16. AsyncAPI servers and documentation

```text
Use the `implement-step` skill.

What to build
Springwolf lists the Kafka and RabbitMQ servers next to the in-memory server; the channels don't change. The README and HOWTO describe the default Docker Compose start, the dev-container limit of the Compose Kafka (don't run the Kafka tests in a dev container while it runs; AGENTS.md gets the same note, as for the workshop), the three settings, the `in-memory` profile, where to look in Grafana, and that old RabbitMQ queues are removed once with `docker compose down -v`. The README's "What this sample doesn't cover" drops "The infrastructure is in memory", and the README's Java blocks that show changed code are updated. Step 8 of `plans/saga-and-outbox/plan.md` links to this spec, and its open questions Q5 and Q7 are marked answered.

Context
- `src/main/resources/application.properties` (Springwolf in-memory server), `README.md`, `HOWTO.md`, `plans/saga-and-outbox/plan.md`.
- The generated AsyncAPI documents checked by CI.

Similar code
- `T:ecommerce/web/AsyncApiTests.java`.

Blocked by
Step 15.

Acceptance criteria
- 43. A reader opens the AsyncAPI document, so that they see where the channels live.
- 43.1. GIVEN the application runs WHEN the reader opens Springwolf UI THEN the document lists the servers of Kafka and RabbitMQ next to the in-memory server.
- 44. A reader follows the HOWTO, so that they can run the sample on each setup.
- 44.1. GIVEN the HOWTO WHEN the reader follows it THEN it explains the default Docker Compose start, the three switches, the in-memory setting and where to look in Grafana.

Tests
- 43.1: asyncApiListsTheKafkaAndRabbitMQServersNextToInMemory - `AsyncApiTests`.
- 44.1: no automated test; the step's review checks the HOWTO against the criterion.

Existing tests this breaks
- `AsyncApiTests` and the generated documents: regenerated with the new servers.
```

---

## Open items for Oskar

These need a decision. The steps that depend on them say so; the rest of the plan runs without them.

- **P1. Answered (Q33):** "Pay now" and "Retry payment" stay plain facade calls with no manual span; they start a new flow. Criteria 33.2 and 33.3 are removed and 36.1 checks two flows split at "Pay now".
- **P3. Answered (Q34):** our own `streams.append` Observation in `KurrentDBEventStore`; the global stays unregistered (step 14).
- **P2. The Docker Compose Kafka in a dev container (plan review Steps-14). Answered (Q32): document the limit, as AGENTS.md does for the workshop; step 16 adds it to the HOWTO and AGENTS.md.** The workshop's Kafka image advertises `localhost:9092`, so in a dev container the tests that find the Compose Kafka fail (AGENTS.md describes this for the workshop). Options: (A) document the limit, as AGENTS.md does for the workshop; (B) advertise a host the dev container can reach; (C) `TestDatabases` doesn't reuse the Compose Kafka. Doesn't block any step; it affects step 8's Compose service and step 16's docs.
