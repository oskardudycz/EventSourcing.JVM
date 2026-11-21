# Plan: Alignment and cleanup of samples/distributed-processes

Revision: 9. Built from spec r13. Step 3 is new (Q13, Q14); the old steps 3-6 are now 4-7.

Spec: `spec.md`. Q&A: `qa.md`. Research: `review/findings.md`.

Oskar asked for as few steps as possible (Q1, Q9), so each step groups one area. Steps run in order; each depends on the one before. Every step ends with the folder's CI command, `./gradlew build eventCatalogTest`, with Gradle's own exit code captured.

## Terms

- **Stream version**: the position of an event in its stream, counted from 0; "no stream" is -1 (Q3).
- **Expected version**: `ExpectedStreamVersion` - `noStream`, `any`, `streamExists` or `at(n)` (Q2).
- **Checkpoint**: `Checkpoint(transactionId, position)`, the one global-position type; KurrentDB and in-memory put 0 into `transactionId`.
- **Type name**: `Class.getName()` (with `$` for nested records), or a module's explicit name (Q6).
- **Module mapping**: a module's `MessageMapping`, returned by its config's static `mapping()`: trusted packages, explicit names, old names with transformations (Q5, Q6).
- **Decider**: `<Name>Decider` with `handle(command, state)` functions returning an array of events; an instance when it needs dependencies (Q7, spec r6).
- **Command handler**: `core/commands/CommandHandler`, which reads a stream, folds it with `evolve`, calls the decider and appends.
- **Summary**: a projection a forwarder reads to add data that the internal events don't carry (README, "The projections behind the external events").
- **Hotel examples**: `hotelmanagement/*`; not changed (Q11).

---

## Step 1. Quick fixes: tracing in the running app, the price calculator, the shop after checkout, the catalog port

```text
Use the `implement-step` skill.

What to build
Four independent bugs from spec section "Bugs" and the Grafana part of the HOWTO:
- The running application gets the OpenTelemetry tracing bridge: `micrometer-tracing-bridge-otel` moves from `testImplementation` to `implementation` in build.gradle; the test-only copy goes.
- HOWTO's Grafana steps say how to find a purchase's `correlationId` (for example from a Loki log line of the purchase, or the `streams.append` span attributes) and that a purchase forms two traces, split at "Pay now". README's line that says to search Tempo for the purchase's `correlationId` with the order id says the same.
- `RandomProductPriceCalculator` uses a `ConcurrentHashMap` and one random generator.
- The shop's order page shows "Your order is being placed" and refreshes every 2 seconds while `OrderStatuses` has no row for the order, instead of answering 404.
- EventCatalog preview and its Playwright tests use port 3001 (eventcatalog config or the preview command, `playwright.config.js` `baseURL` and `webServer.url`), and README and HOWTO say 3001.

Context
- build.gradle: `testImplementation 'io.micrometer:micrometer-tracing-bridge-otel'`.
- ecommerce/shoppingcarts/pricing/RandomProductPriceCalculator.java (`HashMap`, `new Random()` per call), PricingConfig.java.
- ecommerce/shop/ShopPages.java: `checkOut` redirects to `/shop/customers/{customerId}/orders/{orderId}`; `statusOf` throws 404 when there is no row; the order page template under src/main/resources/templates.
- eventcatalog/playwright.config.js, eventcatalog/package.json scripts, build.gradle `eventCatalogPreview` / `eventCatalogTest` tasks; HOWTO.md lines about port 3000 for the catalog and Grafana; README.md line about `eventCatalogPreview` on 3000.
- HOWTO.md Grafana section ("Search by tag for `correlationId`, with the order's id"), README.md around "Search Tempo for the purchase's `correlationId`".

Similar code
- Trace tests: ecommerce/api/PurchaseOnKafkaTraceTests.java, PurchaseOnRabbitMQTraceTests.java, ecommerce/observability/GrafanaExportTests.java.
- Shop pages: ecommerce/web/ShopPagesTests.java, Shopper.java; ShopInBrowserTests.java.
- ecommerce/shoppingcarts/pricing/RandomProductPriceCalculatorTests.java.

Blocked by
None.

Acceptance criteria
- 1.1. GIVEN the main runtime classpath WHEN the application starts THEN the tracer is the OpenTelemetry tracer, and the trace tests pass without a test-only copy of the tracing bridge.
- 1.2. GIVEN the HOWTO WHEN the reader follows its Grafana steps THEN it tells how to find a purchase's `correlationId` and says that a purchase forms two traces, split at "Pay now".
- 10.1. GIVEN many requests that price the same new product at the same time WHEN the calculator prices it THEN every request gets the same price and none fails.
- 11.1. GIVEN a checkout whose order isn't placed yet WHEN the customer is redirected to the order page THEN the page says the order is being placed and refreshes until the order shows.
- 12.1. GIVEN Grafana on port 3000 WHEN the developer runs `eventCatalogPreview` or `eventCatalogTest` THEN the catalog serves and is tested on port 3001.

Tests
- 1.1: no new test (spec Testing Decisions); the existing PurchaseOnKafkaTraceTests, PurchaseOnRabbitMQTraceTests and GrafanaExportTests pass with the bridge only in `implementation`.
- 1.2: no automated test; the step review checks HOWTO and README against the criterion.
- 10.1: `concurrentRequestsForOneNewProductGetOnePrice` - in each of 20 rounds, 50 threads wait on one `CountDownLatch`, then price the same new product; all prices are equal and no thread throws.
- 11.1: `orderPageSaysTheOrderIsBeingPlacedWhileItHasNoStatus` - GET of the order page for an order with no status row returns 200, the "being placed" text and a refresh of 2 seconds; `orderPageShowsTheOrderOnceItHasAStatus` - after the order is placed, the same URL shows the order.
- 12.1: eventCatalogTest passes on 3001 (the existing catalog tests), and `grep 3000 eventcatalog/` finds no catalog URL - checked by the step review.
- extra: `orderPageOfAnotherCustomerIsNotFound` - an existing order of another customer still answers 404.

Existing tests this breaks
- None known; ShopPagesTests has no test for a missing order status.
```

---

## Step 2. The event store contract and the PostgreSQL append function

```text
Use the `implement-step` skill.

What to build
The three event stores implement one `EventStore` interface with the sample's own types, and the PostgreSQL store appends through one SQL function in Emmett's shape. From the spec's "Event store contract (Q2, Q3)", "PostgreSQL store (Q4)", "Subscriptions and checkpoints" (renames only) and the in-memory store bug:
- `ExpectedStreamVersion` (`noStream`, `any`, `streamExists`, `at(long)`); stream versions as `long` from 0, "no stream" -1.
- `Checkpoint(transactionId, position)` moves to `core/eventstore` and is the one global-position type.
- `AppendResult`: `Success(long nextStreamVersion, Checkpoint lastEvent)`, `StreamAlreadyExists(long actual)`, `Conflict(ExpectedStreamVersion expected, long actual)`, `UnexpectedFailure`. `ReadResult`: `Success(events)`, `StreamDoesNotExist`, `UnexpectedFailure`. `NoEventsFound`, `DeleteResult`, `deleteStream`, `setStreamMaxAge` go.
- `core/messaging/EventMetadata` gains `transactionId`.
- In the three stores, a stream read that meets an event it can't map fails with `UnreadableMessageException`.
- Only `KurrentDBEventStore` converts to `StreamState` and `Position`.
- `AggregateStore` (still used by the shop in this step) uses `ExpectedStreamVersion` and the new results, and builds the ETag from the long `nextStreamVersion`. `continueTraceOf` stays on `EventStore` until step 5 removes it with `getAndUpdateContinuingTrace`.
- PostgreSQL: tables `streams(stream_id, stream_position, stream_type)` and `messages(stream_id, stream_position, global_position, transaction_id xid8, message_id, message_kind default 'E', message_type, message_schema_version default 1, message_data, message_metadata, created)` with an index on `(transaction_id, global_position)`; `stream_type` is the stream id's part before the first `-`; function `append_to_stream(...)` as the spec describes (one `pg_current_xact_id()`, version check by `UPDATE ... WHERE stream_position = expected` for `at(n)`, without the position for `streamExists`, `INSERT` for `noStream` with "already exists" on a key violation, insert-or-update for `any`; then one `INSERT ... SELECT FROM unnest(...) WITH ORDINALITY RETURNING global_position`; no `SELECT ... FOR UPDATE`); raw JDBC on HikariCP; Java holds the transaction id as `long`, SQL converts with `::text::xid8`. The poll keeps its current type filter in this step (step 4 changes it), reads `messages`, and its constructor no longer reads the checkpoint.
- `InMemoryEventStore`: one lock around append, subscribe, middleware and dispatch; delivery after the outermost append stays; middleware is a plain list called before subscribers, as in `InMemoryMessageBus`.
- Packages: `core/eventstore` (`EventStore`, its types, `Checkpoint`, `InMemoryEventStore`), `core/eventstore/kurrentdb` (`KurrentDBEventStore`, `KurrentDBSubscriptionToAll` renamed from `AllStreamSubscription`), `core/eventstore/postgresql` (`PostgreSQLEventStore`, `PostgreSQLSubscription`); `core/subscriptions` (`SubscriptionCheckpoint`, `SubscriptionCheckpointRepository`, `SubscriptionCheckpoints`, renamed from the `Processor*` classes; table `processor_checkpoints` unchanged).
- Kept core classes the hotel uses compile against the new types: `CommandBus` and `EventBus` refer to the moved `EventStore`; no hotel class changes. `ShoppingCartFacade` passes its client version to `AggregateStore.getAndUpdate` as `ExpectedStreamVersion.at(...)`.
- HOWTO: the PostgreSQL event store tables changed, so a local PostgreSQL volume is reset once with `docker compose --profile all down -v`; a projections database belongs to one event store type. `PostgreSQLTestDatabase.dataSource()` uses `TestDatabases.newPostgreSQLDatabase()`, and `PostgreSQLEventStoreTests` and `PostgreSQLEventStoreMetadataTests` hold it in a static field: one new database per class, so an old local `streams` table can't break them.
- Deleted (nothing uses them): `KurrentDBCommandBus`, `KurrentDBEventBus`, `KurrentDBSubscription`, `SubscribeToAllOptionsFactory`, the old `core/commands/CommandHandler`, `core/processing/HandlerWithAck`, `HandlerWithAckProcessor`, `core/async/SyncProcessor`; `test/.../shoppingcarts/ShoppingCartTests` (the shared store tests cover appending twice to a new stream). Tick follow-up F1 in `plans/message-broker/follow-ups.md` as done (option A).

Context
- core/kurrentdb/EventStore.java (KurrentDB `StreamState`, `Position`), InMemoryEventStore.java, KurrentDBEventStore.java, PostgreSQLEventStore.java, PostgreSQLSubscription.java, subscriptions/AllStreamSubscription.java.
- core/projections/Checkpoint.java, ProcessorCheckpoint.java, ProcessorCheckpointRepository.java, ProcessorCheckpoints.java.
- core/aggregates/AggregateStore.java (`getAndUpdateContinuingTrace`, `current.version = events.size() - 1`, `ETag.weak(success.nextExpectedRevision())`), core/http/ETag.java, ApiExceptionHandler.java.
- ECommerceApplicationConfig.java (store wiring).

Similar code
- Workshop: workshops/build-your-own-event-store/solved/src/test/java/io/eventdriven/buildyourowneventstore/e04_event_store_methods/postgresql/PostgreSQLEventStore.java (SQL function with `unnest ... WITH ORDINALITY`; note its +1 bug).
- Emmett: `src/packages/emmett-postgresql/src/eventStore/schema/appendToStream.ts`, `tables.ts` (read with `gh api repos/event-driven-io/emmett/contents/<path> -H 'Accept: application/vnd.github.raw'`). Names must not refer to Emmett.
- Shared tests: core/kurrentdb/EventStoreTests.java, DurableEventStoreTests.java and their subclasses.

Blocked by
Step 1.

Acceptance criteria
- 2.1. GIVEN no stream WHEN a module appends two events expecting no stream THEN the result has the next version 1 and the events' global position, and reading the stream returns both events with versions 0 and 1.
- 2.2. GIVEN a stream at version 1 WHEN a module appends expecting no stream THEN the result says the stream already exists and nothing is written.
- 2.3. GIVEN a stream at version 1 WHEN a module appends expecting version 0 THEN the result is a conflict with the expected and the actual version, and nothing is written.
- 2.4. GIVEN a stream at version 1 WHEN a module appends expecting any version, or expecting that the stream exists THEN the events are written at versions 2 and up.
- 2.5. GIVEN no stream WHEN a module appends expecting that the stream exists THEN the result is a conflict and nothing is written.
- 2.6. GIVEN an unknown stream WHEN a module reads it THEN the result says the stream doesn't exist.
- 2.7. GIVEN a stream with an event whose type no module maps or trusts WHEN a module reads the stream THEN the read fails and names the type, instead of skipping the event.
- 2.9. GIVEN an append of three events WHEN it succeeds THEN the result holds the checkpoint (transaction id and global position) of the third event, on every store.
- 2.8. GIVEN two appends to one stream that both expect version 1 WHEN they run at the same time THEN exactly one succeeds and the other gets a conflict, on each store, the in-memory store included.
- 3.1. GIVEN a new stream `Order-<id>` WHEN a module appends three events THEN one call of the append function writes the stream row with type `Order` and version 2, and three message rows with versions 0-2, kind `E`, the type name (for example `io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent$OrderPlaced`), schema version 1, one transaction id and increasing global positions.
- 3.2. GIVEN an append whose version check fails WHEN the function runs THEN no message row is written and the stream row is unchanged.

Tests (in the shared `EventStoreTests`, run for in-memory, KurrentDB and PostgreSQL, unless named otherwise; tests that need a raw stored event go into `DurableEventStoreTests`, which runs for KurrentDB and PostgreSQL)
- 2.1: `appendingTwoEventsToANewStreamReturnsVersionOneAndTheyReadBackAtZeroAndOne`.
- 2.2: `appendingExpectingNoStreamToAnExistingStreamSaysItAlreadyExistsAndWritesNothing`.
- 2.3: `appendingWithAStaleVersionIsAConflictWithBothVersionsAndWritesNothing`.
- 2.4: `appendingExpectingAnyVersionWritesAfterTheLastEvent`, `appendingExpectingTheStreamExistsWritesAfterTheLastEvent`.
- 2.5: `appendingExpectingTheStreamExistsToNoStreamIsAConflict`.
- 2.6: `readingAnUnknownStreamSaysItDoesNotExist`.
- 2.7: `DurableEventStoreTests.readingAStreamWithAnUnknownTypeFailsNamingTheType` - asserts the failure carries the stored type name (the store writes a row or event with a made-up type through the store's own client or SQL). The in-memory store gets its 2.7 test in step 4, when it reads through the mapper.
- 2.9: `appendingThreeEventsReturnsTheCheckpointOfTheThird` - asserts the checkpoint equals the third event's metadata (position, and transaction id on PostgreSQL).
- 2.8: `concurrentAppendsWithTheSameVersionLetExactlyOneWin` - in each of 20 rounds, on a new stream at version 1, two threads wait on one `CountDownLatch` (as `parallelAppendsToTwoStreamsBothSucceed` does), then append expecting version 1; one `Success`, one `Conflict`; the stream has one of the two batches.
- 3.1: `PostgreSQLEventStoreTests.appendingThreeEventsWritesOneStreamRowAndThreeMessageRowsInOneTransaction` - asserts the `streams` and `messages` rows with SQL; in this step the type name is the one the store writes today (`EventTypeMapper.toName`), step 4 changes the assertion to `Class.getName()`.
- 3.2: `PostgreSQLEventStoreTests.aFailedVersionCheckWritesNoMessageRows`.
- extra: the store-neutral tests now only in InMemoryEventStoreTests move into EventStoreTests (`readOnUnknownStream...`, `appendWithNoStreamOnExistingStream...`, `appendWithAnyRevision...`, `appendWithStreamExists...`, `appendReturnsRevision...`, `notifiesByExactRuntimeClass...`, `failedAppendNotifiesNobody`).

Existing tests this breaks
- Every test that uses `StreamState`, `Position`, `NoEventsFound` or the old packages: update imports and types.
- AggregateStoreTests: new store types.
- PostgreSQLEventStoreTests that query `events` or `transaction_id` by hand: use the `Checkpoint` in the append result. `eventOfATransactionThatCommitsLastIsNotSkipped` inserts into `streams(id, ...)` and `events` by hand inside two open transactions: rewrite it to call `append_to_stream` inside those two transactions.
- ProcessorCheckpointsTests: renamed with the class.
- AGENTS.md and README links to moved classes: update paths.
```

---

## Step 3. Modules configured on their own, and tests that wait on catch-up instead of time

```text
Use the `implement-step` skill.

What to build
From the spec's "Module configuration (Q14)", "Catch-up waits (Q13)" and the Testing Decisions on waits and `MessageCatcher`. Already done before step 2 was committed, at Oskar's request (keep it, don't redo it): `MessageBus.awaitCaughtUp(Duration)` on the three buses (Kafka's check through a `RetryTemplate`, Q16), `MessageBusTests.awaitLog` on top of it, the fixed waits removed from `RabbitMQMessageBusDeadLetterOutageTests` and `NoOtlpEndpointTests`, and `maxParallelForks = 2`; the tool-behaviour test classes are deleted and Kafka test containers have no consumer group join delay (Q17). This step adds:
- `EventStore.subscribe` returns a `Subscription` with `awaitProcessed(Checkpoint, Duration)`: returns once the subscription has handled the event at that checkpoint or a later one; fails after the timeout naming the subscription. In-memory, KurrentDB and PostgreSQL track the last handled checkpoint. `Checkpoint` gets an order: transaction id, then position (as the PostgreSQL poll). `close()` returns only after the subscription has stopped.
- Every remaining fixed wait goes: `EventStoreTests` (`aSubscriptionNotifiesByTheExactClassOfTheEventNotByItsSupertype`, `aFailedAppendNotifiesNobody`), `DurableEventStoreTests` (three windows), `KurrentDBEventStoreTests` (one), and any other `Thread.sleep`, `during` or `pollDelay` in `src/test`. A test that proves an event was not delivered appends one more event of the subscribed type, waits for its checkpoint with `awaitProcessed`, then asserts once. A test with a handler that always fails asserts once after `close()` returns. `awaitCheckpointToSettle` waits with `awaitProcessed` for the last event appended before Kafka stops.
- `testing/MessageCatcher`, copied from the workshop's `sagas_rabbitmq/core/MessageCatcher`, without its polling: callers wait with `awaitCaughtUp` first. `MessageBusTests` and the broker bus and failure test classes subscribe `catcher::catchMessage` (or a catcher per handler where the test checks which handler ran) and assert on the messages; the `log` string lists and `awaitLog` go.
- `ShoppingCartsConfig`, `OrdersConfig`, `PaymentsConfig`, `ShipmentsConfig` become `@Configuration` classes: their facade, projections, forwarder, workers and repositories as beans; their handlers and store subscriptions subscribed when their beans are created; static `@Bean` methods for `MessageRoutes`, `MessageChannel(name)` and `MessageQueue(name, channel, routingPattern)` (new records in `core/messaging`). A queue on another module's channel uses that module's public channel constant (`ShoppingCartsConfig.external`, `PaymentsConfig.responses`, `ShipmentsConfig.responses`). `OrdersConfig` subscribes the saga from a `SmartInitializingSingleton` bean, after all singletons, so the forwarders come first. `EventStore.subscribe` callers that chain it call it once per statement.
- The three buses take the lists of `MessageRoutes`, `MessageChannel` and `MessageQueue` in their constructors and create all channels, then all queues, before they start; `routes`, `channel` and `bind` leave `MessageBus` and the buses. On Kafka every channel gets its topic, so a queue bound to another module's channel works whatever the order (criterion 17.2).
- `ECommerceConfig` and `ECommerceModule` are deleted; `ECommerceApplicationConfig` keeps the shared infrastructure (store, bus, projections database, serializer, observability) and builds the bus from the injected lists.
- Tests that call a module's static `configure(...)` (21 files) call the module configuration's bean methods as plain Java instead, with the same dependencies, and subscribe the saga last (no new test seam, Q9); the bus tests pass the lists to the bus constructor through their `createBus`. A test that waits for the end of a chain waits on its last observable result with Awaitility `atMost`; `awaitCaughtUp` / `awaitProcessed` serve the "nothing more happens" checks.
- README "Channels and queues" Java block and the composition root paragraph show the module configuration; HOWTO unchanged unless it names `ECommerceConfig`.

Context
- ecommerce/ECommerceApplicationConfig.java, ECommerceConfig.java, ECommerceModule.java, the four module `*Config.java`, OrdersConfig.configureProcess.
- core/messaging/MessageBus.java, InMemoryMessageBus.java, KafkaMessageBus.java (`bind`, `addContainer`, `addRoutingContainer`, `declareTopic`), RabbitMQMessageBus.java (`bind`, `createContainer`), MessageRoutes.java.
- core/eventstore (after step 2): EventStore.subscribe and the three subscriptions.
- test: MessageBusTests and subclasses, KafkaMessageBusFailureTests, RabbitMQMessageBusFailureTests, TestRabbitMQ.busOn, TestKafka, RouteRecordingMessageBus, ECommerceTests, the `*ConfigTests` and other callers of `Config.configure` (`grep -rl "Config.configure\|configureProcess" src/test`).

Similar code
- Workshop: workshops/event-driven-architecture/solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses/sagas_kafka/solution2_immutableentities/KafkaSolution2Configuration.java and HotelFinancialMessageRoutes.java (module configuration and `MessageRoutes` bean), sagas_rabbitmq/core/MessageCatcher.java, sagas_rabbitmq/core/RabbitConfig.java (declarations created at start).
- Emmett's `whenProcessed` / `whenCaughtUp`: src/packages/emmett/src/consumers/consumers.ts (read with `gh api repos/event-driven-io/emmett/contents/<path> -H 'Accept: application/vnd.github.raw'`); names must not refer to Emmett.

Blocked by
Step 2.

Acceptance criteria
- 16.1. GIVEN messages published to queues that a bus consumes WHEN a caller waits for the bus to catch up THEN the wait returns once every one of them is handled or dead-lettered, on the in-memory bus, Kafka and RabbitMQ.
- 16.2. GIVEN a Kafka or RabbitMQ bus whose handler doesn't finish WHEN a caller waits for it to catch up with a timeout THEN the wait fails after the timeout and names the queue or the consumer group.
- 16.3. GIVEN a store subscription and an appended event of its types WHEN a caller waits for the event's checkpoint THEN the wait returns once the subscriber has handled it, on the three stores; with a timeout it fails instead of hanging.
- 16.4. GIVEN the test suite WHEN it runs THEN no test waits a fixed time: no `Thread.sleep`, no Awaitility `during` or `pollDelay`.
- 17.1. GIVEN the application WHEN it starts THEN its context holds the `MessageChannel` beans `shopping_carts-external`, `orders-internal`, `payments-requests`, `payments-responses`, `payments-internal`, `shipments-requests`, `shipments-responses`, `shipments-internal` and the `MessageQueue` beans of the README's channel table, each declared by the configuration class of the module that owns it, plus one `MessageRoutes` bean per module.
- 17.2. GIVEN a module that binds a queue to another module's channel WHEN the application starts on Kafka or RabbitMQ THEN the queue receives the messages published to that channel, whichever module is configured first.
- 17.3. GIVEN the in-memory store and bus WHEN a cart is checked out THEN `ShoppingCartCheckedOut` is published before `PlaceOrder`, because the forwarders subscribe before the saga (protects existing behaviour).

Tests
- 16.1: every `MessageBusTests` scenario now waits with `awaitCaughtUp` and asserts once (in-memory, Kafka, RabbitMQ); `KafkaMessageBusFailureTests.aDeadLetteredMessageCountsAsCaughtUp` and the RabbitMQ twin: a message that is dead-lettered, then `awaitCaughtUp` returns and the dead-letter topic or queue holds it.
- 16.2: `KafkaMessageBusTests.waitingForCatchUpFailsNamingTheGroupWhenAHandlerDoesNotFinish` and the RabbitMQ twin naming the queue - a handler blocks on a latch the test releases after the assertion.
- 16.3: `EventStoreTests.awaitingAnAppendedCheckpointReturnsOnceTheSubscriberHandledIt`, `EventStoreTests.awaitingAnEarlierCheckpointReturnsOnceALaterEventIsHandled` and `EventStoreTests.awaitingACheckpointFailsAfterTheTimeoutWhenTheSubscriberFails` (a handler that always throws; timeout 1 s; asserts the failure names the subscription); `DurableEventStoreTests.closingAStoreReturnsOnlyAfterItsSubscriptionStopped` (attempts counted at `close()` return stay the same when read again).
- 16.4: the step review greps `src/test` for `Thread.sleep`, `during(` and `pollDelay`.
- 17.1: `ECommerceModulesTests.eachModuleDeclaresItsOwnChannelsAndQueues` - a `@SpringBootTest` on the in-memory profile reads the `MessageChannel` and `MessageQueue` beans and, through the bean factory, the configuration class that declares each; asserts names and owners as the README table lists them; the step review checks that no module configuration injects another module's beans.
- 17.3: the existing `ECommerceTests` order checks (cart forwarder before the saga's `PlaceOrder`).
- 17.2: `KafkaMessageBusTests.aQueueBoundToAChannelDeclaredByALaterModuleGetsItsMessages` and the RabbitMQ twin - the queue's module comes first in the lists.
- extra: the existing ECommerceTests, ECommerceSettingsTests, the API tests and the trace tests pass on the new wiring.

Existing tests this breaks
- Every bus test that calls `routes`, `channel` or `bind`: pass the lists through `createBus`.
- The 21 test files that call `Config.configure` or `configureProcess`.
- Tests that use `log` / `awaitLog` in the bus and failure test classes.
- ECommerceRoutesTests (routes now come from the module beans).
- The `EventStoreTests` and `DurableEventStoreTests` windows named above.
- `ShoppingCartsModule`, `OrdersModule`, `PaymentsModule`, `ShipmentsModule` and `ECommerceModule` are deleted with `ECommerceConfig`; tests that read them use the configuration's beans.
```

---

## Step 4. One type mapper and serializer, and store subscriptions by type name

```text
Use the `implement-step` skill.

What to build
From the spec's "Type names and serialization (Q5, Q6)" and the subscription rules:
- Type name = `Class.getName()` everywhere, or a module's explicit name.
- Each module configuration (`ShoppingCartsConfig`, `OrdersConfig`, `PaymentsConfig`, `ShipmentsConfig`, Spring configurations since step 3) gets a static `mapping()` returning its `MessageMapping`, exposed by a static `@Bean` method (criterion 17.4): its trusted packages (the module package and every sub-package that holds a message class, listed one by one), explicit names, and old names with transformations. A transformation names the class it produces (`register(oldName, OldClass, CurrentClass, transform)`, `register(oldName, CurrentClass, jsonTransform)`). `ECommerceMessages` is deleted.
- `MessageTypeMapper` (the workshop's `MessageMappingRegistry` role) merges the module mappings; name to class (explicit, old, else trusted class name), class to name, class to all its names; two classes under one explicit name fail the start, naming both. The shared messaging configuration builds it from the injected `MessageMapping` beans before it creates the serializer, buses and stores.
- One `JsonMapper` bean (`FAIL_ON_UNKNOWN_PROPERTIES=false`, `WRITE_DATES_AS_TIMESTAMPS=false`, `ADJUST_DATES_TO_CONTEXT_TIME_ZONE=false`); the in-memory store's own mapper goes. `MessageSerializer` is one bean on it; the three stores use it (in-memory included, so messages round-trip as on the durable stores). Spring's Kafka and RabbitMQ serializers get the same `JsonMapper`, as in the workshop's `MessageSerializationConfiguration`.
- Brokers: Kafka uses Spring's `JacksonJsonSerializer` / `JacksonJsonDeserializer` with `DefaultJacksonJavaTypeMapper` (type id first, the mapper's names, trusted packages); RabbitMQ uses Spring's JSON message converter with `DefaultClassMapper` configured the same way; old names go through `VersionedMessageConverter` and `MessageTransformations`, as in the workshop. `__TypeId__` replaces the `messageType` header; the dead-letter counter's `message.type` tag reads it. The Kafka routing consumer copies records with their headers.
- Derived message ids use the type name; a published message's id is derived only from a real handled message id, else random.
- Store subscriptions filter on the type names of their classes (current, explicit, old): KurrentDB keeps its server-side filter (built from those names); PostgreSQL drops the SQL type filter, reads batches, filters names in Java, and moves its in-memory cursor over the whole batch; checkpoints only for handled events. An unknown name is passed over; a known name whose data can't be read is a handler failure and is retried.
- `serialization/EventTypeMapper` and `serialization/EventSerializer` are deleted. Kept `EntityStore` (hotel) takes a `MessageSerializer` in its constructor, keeps its raw KurrentDB client, its own `StreamState` conversion and passes over events it can't read, as now. No hotel class changes.
- `TestMessages` builds the mapper from the module mappings a test needs; tests with their own test-only message classes (`AggregateStoreTests`, the store tests) build a mapping that trusts their own test package.
- Regenerate the AsyncAPI documents and the EventCatalog copies if the type header changes them (step 7 regenerates them again at the end).

Context
- core/serialization/EventTypeMapper.java, EventSerializer.java; core/messaging/versioning/MessageMapping.java, MessageSerializer.java, MessageTransformer.java; ecommerce/ECommerceMessages.java; ECommerceApplicationConfig.java (`new MessageSerializer(ECommerceMessages.mapping(), EventSerializer.mapper, ...)` twice); KafkaMessageBus.java, RabbitMQMessageBus.java (`MESSAGE_TYPE` header, `readMessage`, `typeNameOf`); MessageContext.java (`"%s:%s:%d".formatted(handledMessageId, messageType.getTypeName(), position)`, `continuing`); KurrentDBEventStore.subscribe (regex filter); PostgreSQLSubscription (SQL type filter, cursor); InMemoryEventStore (own `JsonMapper`); core/entities/EntityStore.java; test/testing/TestMessages.java; web/MessageBusClassScanner, EventCatalogMessageTypes, ModuleOperations (they read type names or classes).

Similar code
- Workshop: workshops/event-driven-architecture/solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses/sagas_kafka/core/versioning/* (`MessageMapping`, `MessageMappingRegistry`, `MessageSerializationConfiguration`, `MessageTransformations`), sagas_kafka/core/VersionedMessageConverter.java, sagas_rabbitmq/core/RabbitConfig.java and VersionedMessageConverter.java, sagas_kafka/solution1_aggregates/GroupCheckoutMessageTypeMapping.java.
- Tests: core/messaging/versioning/MessageSerializerTests.java, ECommerceMessagesTests.java, KafkaMessageBusTests / RabbitMQMessageBusTests (type name scenarios), EventStoreTests.

Blocked by
Step 3.

Acceptance criteria
- 4.1. GIVEN a PostgreSQL subscription for one event type and many later events of other types WHEN one more event of the subscribed type is appended THEN the subscription handles it. This criterion protects existing behaviour; the rescan fix (no type filter in SQL, the cursor moves over the whole batch) is checked by the step review.
- 4.2. GIVEN a stored event whose type name maps to a subscribed class but whose data can't be read WHEN the subscription reaches it THEN it treats it as a handler failure and retries with back-off, and its checkpoint doesn't move past the event.
- 4.3. GIVEN a stored event whose type name maps to no class WHEN a subscription reads past it THEN the subscription passes over it and handles the next event of its types.
- 4.4. GIVEN an event stored under an old name that a module maps to the current class WHEN a subscription to the current class reads it THEN the subscription handles it as the current class.
- 5.1. GIVEN `PaymentExternalEvent.PaymentAuthorized` with no explicit name WHEN it is published or stored THEN its type name is `io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent$PaymentAuthorized`, on both brokers and all three stores, and a reader in the orders module trusts it.
- 5.2. GIVEN a module's mapping that gives a class an explicit name WHEN the message is published or stored THEN that name is used, and readers map it back to the class.
- 5.3. GIVEN a module's mapping of an old name to the current class with a transformation WHEN a message with the old name is read from a bus or a store THEN the reader gets the current class.
- 5.4. GIVEN a message whose class name is outside the modules' packages WHEN a bus reads it THEN it is dead-lettered as unreadable.
- 5.5. GIVEN two modules that map the same explicit name to different classes WHEN the application starts THEN the start fails and names both classes.
- 17.4. GIVEN the application WHEN it starts THEN its context holds one `MessageMapping` bean per module, declared by that module's configuration class, and the type mapper is built from those four.

Tests
- 4.1: `PostgreSQLEventStoreTests.aSubscriptionHandlesItsTypeAfterManyEventsOfOtherTypes` - 500 events of another type, then one of the subscribed type; the subscriber gets exactly that one.
- 4.2: `DurableEventStoreTests.aSubscribedEventThatCantBeReadIsRetriedAndTheCheckpointStays` - a stored event with broken data under the type name of a test-only event class that no other test subscribes to (a new subscription starts from the start, so a shared type would block the other tests); asserts the read is tried more than once and the stored checkpoint stays before the event.
- 4.3: `DurableEventStoreTests.aSubscriptionPassesOverAnEventWithAnUnknownName` - a raw event with a made-up name, then one of the subscribed type; the subscriber gets only the second.
- 4.4: `DurableEventStoreTests.aSubscriptionHandlesAnEventStoredUnderAnOldName` - a test mapping maps `old.Name` to the current class with a transformation; the subscriber receives the current class.
- 5.1: `MessageTypeMapperTests.aNestedRecordIsNamedByItsJavaClassName`; `BrokerMessageBusTests.aPublishedMessageCarriesItsClassNameAsTypeId` - publishes `PaymentExternalEvent.PaymentAuthorized` with `PaymentsConfig.mapping()`; `DurableEventStoreTests.aStoredEventCarriesItsTypeName`; step 2's 3.1 test asserts the new name; the step review checks the in-memory store's type names; `ECommerceMessagesTests` replaced by `ECommerceMappingsTests.ordersTrustTheExternalEventsOfPaymentsShipmentsAndCarts`.
- 2.7 (in-memory): `InMemoryEventStoreTests.readingAnEventOfAnUntrustedPackageFailsNamingTheType` - a store whose mapping doesn't trust the event's package; the read fails and names the type.
- 5.2: `MessageTypeMapperTests.anExplicitNameIsWrittenAndReadBack`; `BrokerMessageBusTests.anExplicitNameTravelsAndIsReadBack`.
- 5.3: the existing `KafkaMessageBusFailureTests` and `RabbitMQMessageBusFailureTests` `oldVersionOfAMessageIsTransformedToTheCurrentOne`, on `__TypeId__`; `DurableEventStoreTests.aStoredEventWithAnOldNameIsReadAsTheCurrentClass`.
- 5.4: the existing `typeOutsideTheSamplesPackageIsNotCreatedAndIsDeadLettered` in both failure test classes, on `__TypeId__` and the trusted packages.
- 5.5: `MessageTypeMapperTests.twoClassesUnderOneExplicitNameFailNamingBoth`.
- 17.4: `ECommerceModulesTests.eachModuleDeclaresItsOwnTypeMapping` - in the started application, four `MessageMapping` beans, each declared by its module's configuration class.
- `BrokerMessageBusTests` is a new abstract base between `MessageBusTests` and the Kafka and RabbitMQ bus test classes, as `DurableEventStoreTests` is for the stores. It is made by moving the six scenarios now copied in `KafkaMessageBusTests` and `RabbitMQMessageBusTests` into it (type name to class, published type name, message starting a process, message published while handling, handler in the trace, message without a type header); the two type name scenarios merge with the 5.1 and 5.2 tests above.
- extra: `EventStoreTests.anOffsetDateTimeKeepsItsOffset` (all three stores); `BrokerMessageBusTests.anOffsetDateTimeKeepsItsOffset`. The message id rules (type name, random without a handled message) are checked by the step review, as the spec says.

Existing tests this breaks
- ECommerceMessagesTests, MessageSerializerTests (class-name fallback test `aClassNameInsideTheTrustedPackageIsRead`: keep, reworded to the trusted-package rule), tests that read the `messageType` header (KafkaMessageBusTests, RabbitMQMessageBusTests, KafkaMessageBusFailureTests, RabbitMQMessageBusFailureTests, redelivery tests, ECommerceSettingsTests dead-letter tags): switch to `__TypeId__`.
- About 10 tests that call `TestMessages.serializer(ECommerceMessages.mapping())`: use the module mappings. About 29 test files that call `new InMemoryEventStore(context)`: pass a serializer from `TestMessages` whose mapping trusts the packages they store (the module mappings, or the test's own package for test-only events).
- The Kafka and RabbitMQ test classes extend `BrokerMessageBusTests` instead of `MessageBusTests`.
- AsyncApiTests and the generated documents if the type header changes them; EventCatalog tests if names change.
```

---

## Step 5. The shop on command handlers and immutable entities

```text
Use the `implement-step` skill.

What to build
From the spec's "Command handlers and immutable entities (Q7)", the order payment bug and "Requester's id (Q8)":
- In the four shop modules, each entity (`ShoppingCart`, `Order`, `Payment`, `Shipment`) becomes a record with static `initial()` and `evolve(state, event)`; its rules move into a `<Name>Decider` with `handle(command, state)` functions returning an array of events (as `EventSourcedSpecification` and the hotel deciders use). The four facades pass the time today, so every shop decider is an instance with the clock, plus: `ShoppingCartDecider` the price calculator, `OrderDecider` `PaymentTerms`, `PaymentDecider` `PaymentValidity`, `ShipmentDecider` the product availability check. Late or repeated commands return no events and don't throw.
- `core/commands/CommandHandler<State, Command, Event>`: takes the event store, the message context, the stream id mapping, `initial`, `evolve` and the decider function; reads, folds, decides, appends with the version read; without a client version retries a conflict up to 3 attempts in all, through Spring Framework's `RetryTemplate` (`RetryPolicy.builder().includes(ConcurrentModificationException.class).maxRetries(2).delay(Duration.ZERO)`, called with `invoke`), as the hotel services use it; it throws `ConcurrentModificationException` for `AppendResult.Conflict`, as `AggregateStore` does, so the 412 mapping stays; with a client version appends with it and reports a conflict (the API answers 412); returns the next version for the ETag; opens the continuing scope from the last event for the commands that do so today; `get(id)` returns the folded state (`PaymentFacade.createCheckoutSession`, `ShoppingCartFacade.isCheckedOut`).
- Facades call command handlers. The shop no longer uses `AbstractAggregate`, `Aggregate`, `AggregateStore`; they stay in core for the hotel; `AggregateStore` loses `getAndUpdateContinuingTrace`, keeps `get(id)`, `getAndUpdate(id, handle)` and `getAndUpdate(id, expectedVersion, handle)` (adapted to `ExpectedStreamVersion` in step 2), and its hand-written retry loop becomes the same `RetryTemplate` (Q16).
- `CommandHandler` and the shop facades return the next stream version as a `long`; `ShoppingCartsController` writes `new ETag(String.valueOf(version), true).formattedTag()` and reads `If-Match` with Spring's `ETag.parse` (first tag's number; empty or non-numeric throws `IllegalArgumentException`, answered 400 as today). The core `ETag` stays for the hotel (Q16). Test helpers that use the core `ETag` for the shop switch to Spring's: `ApiSpecification`, `PurchaseRestBuilder` (`BuilderResult.cartETag`), `RemoveProductItemFromShoppingCartTests`, `CheckOutShoppingCartTests`. `continueTraceOf` leaves `EventStore` and its three implementations. `AggregateSpecification` is deleted; shop domain tests use `EventSourcedSpecification` with the same scenarios.
- Order: `RecordOrderPaymentFailure` and `RecordOrderPaymentCapture` carry the `PaymentId`; the order ignores them for any payment other than its current one; the first payment id is set when the order is placed, with the saga's derivation (`PaymentId.derivedFrom(orderId.value())`); `OrderSaga` passes `event.paymentId()`.
- Shipments: internal events keep `referenceId` only in `StockReserved` and `ProductWasOutOfStock`; `FulfillmentRequested`, `FulfillmentRejected`, `PackageWasSent`, `PackageWasDelivered`, `StockReleased` lose it; a `ShipmentSummary` projection (`external/`, as `PaymentSummary`) holds the shipment id and the requester's id; `ShipmentExternalEventForwarder` reads it with the same "summary has caught up" check as `PaymentExternalEventForwarder`; external events don't change.

Context
- ecommerce/*/ShoppingCart.java, Order.java, Payment.java, Shipment.java (aggregates on `AbstractAggregate`), their facades and `*Config` (Spring configurations since step 3, `new AggregateStore<...>` in their bean methods), OrderSaga.java (`onPaymentFailed`, `onPaymentCaptured`), OrderCommand.java, ShipmentEvent.java, shipments/external/ShipmentExternalEventForwarder.java.
- core/aggregates/AggregateStore.java, test/testing/AggregateSpecification.java, EventSourcedSpecification.java, AggregateStoreTests.java.

Similar code
- samples/event-sourcing-kurrentdb-simple/src/main/java/io/eventdriven/ecommerce/core/entities/CommandHandler.java, shoppingcarts/ShoppingCart.java, ShoppingCartDecider.java, ShoppingCartsConfig.java.
- Hotel saga style deciders (read only): hotelmanagement/saga/gueststayaccount/GuestStayAccountDecider.java.
- `RetryTemplate` usage: hotelmanagement/saga/groupcheckout/GroupCheckoutService.java (read only); Spring docs: framework-docs core/resilience.adoc "Programmatic Retry Support".
- Payments summary: payments/external/PaymentSummaries.java, PaymentSummary.java, PaymentExternalEventForwarder.java, PaymentSummariesTests.java, PaymentExternalEventForwarderOrderingTests.java.

Blocked by
Step 4.

Acceptance criteria
- 6.1. GIVEN a shopping cart opened with one product (version 0) WHEN `AddProductItemToShoppingCart` is handled THEN the decider gets the cart with one product, `ProductItemAddedToShoppingCart` is appended expecting version 0, and the next version is 1.
- 6.2. GIVEN a command that arrives late or twice WHEN it is handled THEN the decider returns no events, nothing is appended, and nothing is thrown.
- 6.3. GIVEN a concurrent append between the read and the append WHEN a command without a client version is handled THEN it is read and decided again, up to 3 attempts in all, and the third conflict fails the command.
- 6.4. GIVEN a client version in `If-Match` that is not the current one WHEN the command is handled THEN the API answers 412, as today.
- 6.5. GIVEN a cart opened with one product WHEN the client reads it THEN the ETag is `W/"0"`, and after one more product `W/"1"`, as today.
- 8.1. GIVEN a failed first payment and a retry with a second payment that is still pending WHEN the failure of the first payment arrives again THEN the order records nothing.
- 8.2. GIVEN a confirmed order paid with the second payment WHEN the failure of the first payment arrives THEN the order records nothing and is not canceled.
- 8.3. GIVEN an order whose current payment is the second one WHEN a capture of the first payment arrives THEN the order records nothing.
- 9.1. GIVEN a shipment requested for an order WHEN stock is reserved, the fulfillment is requested or rejected, the package is sent or delivered, or stock is released THEN only the event that records the request (`StockReserved` or `ProductWasOutOfStock`) stores the requester's id.
- 9.2. GIVEN a shipment whose package was sent WHEN the forwarder publishes `PackageWasSent` THEN the external event carries the requester's id, read from the shipment summary.
- 9.3. GIVEN a shipment summary that hasn't caught up WHEN the forwarder handles an event THEN it throws, and the event is delivered again after the summary catches up, as for payments.

Tests
- 6.1: `CommandHandlerTests.handlingACommandFoldsTheStreamAndAppendsWithTheVersionRead` (in-memory store, a test entity in the test package) and `ShoppingCartFacadeTests.addingAProductToACartWithOneProductAppendsAtVersionOne`.
- 6.2: `CommandHandlerTests.aCommandThatDecidesNothingAppendsNothing`; decider tests per module for late or repeated commands (kept scenarios from OrderTests, PaymentTests, ShipmentTests, ShoppingCartTests moved to `EventSourcedSpecification`).
- 6.3: `CommandHandlerTests.aConflictWithoutAClientVersionIsRetriedUpToThreeAttempts` - a store wrapper that appends a competing event before each of the first two appends; the third attempt succeeds; a variant with three competing appends fails the command.
- 6.4: existing `CheckOutShoppingCartTests` 412 test and `CommandHandlerTests.aConflictWithAClientVersionIsNotRetried`.
- extra: the `AggregateStore` change to `RetryTemplate` is a refactor guarded by the existing `AggregateStoreTests` (`aConflictWithoutAnExpectedVersionIsRetriedOnTheNewState`, `aConflictThatKeepsHappeningFailsAfterThreeAttempts`, `aConflictWithAnExpectedVersionIsNotRetried`); `CheckOutShoppingCartTests.aMalformedIfMatchIsABadRequest` (400, if no test covers it yet).
- 6.5: existing GetCartTests / AddProductItemToShoppingCartTests ETag assertions (`W/"0"`, `W/"1"`).
- 8.1: `OrderTests.aRepeatedFailureOfTheFirstPaymentDuringTheRetryRecordsNothing`.
- 8.2: `OrderTests.aFailureOfTheFirstPaymentAfterTheSecondConfirmedTheOrderRecordsNothing`.
- 8.3: `OrderTests.aCaptureOfTheFirstPaymentWhileTheSecondIsCurrentRecordsNothing`.
- 9.1: `ShipmentTests.onlyTheReservationEventsStoreTheRequester` - three paths that can happen: reserve, request fulfillment, rejected, release stock; reserve, request fulfillment, sent, delivered; out of stock. Asserts the recorded events equal the expected records, where only `StockReserved` and `ProductWasOutOfStock` carry the requester id.
- 9.2: `ShipmentExternalEventForwarderTests.packageWasSentCarriesTheRequesterIdFromTheSummary`.
- 9.3: `ShipmentExternalEventForwarderOrderingTests.theForwarderWaitsUntilTheSummaryHasCaughtUp` (as PaymentExternalEventForwarderOrderingTests).
- extra: `ShipmentSummariesTests` (as PaymentSummariesTests); `OrderSagaTests.aPaymentFailurePassesThePaymentId`; `CommandHandlerTests.readingAnEntityReturnsItsStateAfterAllEvents`; the continuing trace scope is covered by the existing PurchaseOnKafkaTraceTests and PurchaseOnRabbitMQTraceTests.

Existing tests this breaks
- OrderTests, PaymentTests, ShipmentTests, ShoppingCartTests (ecommerce): from `AggregateSpecification` to `EventSourcedSpecification`, same scenarios.
- OrderSagaTests, ShipmentFacadeTests, PaymentFacadeTests, ShoppingCartFacadeTests, OrderFacadeTests: new facade wiring.
- ShipmentExternalEventForwarderTests, ECommerceTests: shipment events without `referenceId`.
- ShipmentsConfigTests: the `ShipmentsConfig` bean method that builds the forwarder gets the shipment summary repository; the test passes one from `ProjectionsDatabase`.
- AggregateStoreTests: unchanged in this step.
- README Java blocks that show aggregates, the saga or the forwarders: update.
```

---

## Step 6. Broker alignment: dead-letter tags, RabbitMQ retry, in-memory step spans

```text
Use the `implement-step` skill.

What to build
From the spec's "Messaging":
- The Kafka dead-letter counter tags `messaging.destination.name` with the queue, as RabbitMQ does.
- RabbitMQ retries through Spring AMQP's listener retry (`RetryInterceptorBuilder.stateless()` with the configured interval and attempts, `MessageFailureStrategy` as the retry predicate, a recoverer that counts and rejects), as the workshop's `RabbitConfig`; the hand-written loop and `Thread.sleep` go. The message-broker spec's failure criteria keep holding.
- `KafkaMessageBus.close()` closes the routing producer too.
- The in-memory bus opens the step span with `context.consuming`, as the brokers do.
- `MessageRouter`'s unused convention function and `firstRecordComponent` go (with their tests). Routes per module by package with `inPackage` stay; one `publish`; the buses stay separate classes.

Context
- core/messaging/KafkaMessageBus.java (dead-letter counter tags `record.topic()`, `connectingTo` with a second producer factory, `close()`), RabbitMQMessageBus.java (`while (true)` retry, `pause`), InMemoryMessageBus.java (`context.handling`), MessageRouter.java, MessageContext.java (`consuming`).

Similar code
- Workshop: sagas_rabbitmq/core/RabbitConfig.java (`retryUnlessTheStrategyDeadLetters`, `deadLetterRecoverer`); sagas_kafka/core/KafkaConfiguration.java.
- Tests: KafkaMessageBusFailureTests, RabbitMQMessageBusFailureTests, ECommerceSettingsTests dead-letter tests, MessageBusTests.

Blocked by
Step 5.

Acceptance criteria
- 13.1. GIVEN a message dead-lettered from queue `orders-shopping_carts-external` WHEN the counter `messaging.dead_lettered` grows THEN its destination tag is the queue name on Kafka and on RabbitMQ.
- 13.2. GIVEN a RabbitMQ handler that always fails WHEN a message arrives THEN it is retried with the configured interval and attempts through Spring AMQP's listener retry and then dead-lettered, as the message-broker spec's criteria require.
- 15.1. GIVEN a subscriber on the in-memory bus WHEN it handles a message THEN a step span named `handle <MessageType>` wraps the handler, as on Kafka and RabbitMQ.

Tests
- 13.1: `KafkaMessageBusFailureTests.deadLetterCountIsTaggedWithTheStableTypeNameAndTheSourceTopic` becomes `deadLetterCountIsTaggedWithTheStableTypeNameAndTheQueue` and asserts the queue name, with a queue named after a channel of another module (`orders-shopping_carts-external` bound to `shopping_carts-external`); the existing RabbitMQ test of the same name uses the same queue and channel.
- 13.2: `RabbitMQMessageBusFailureTests.aFailingHandlerIsRetriedWithTheConfiguredIntervalThenDeadLettered` - records attempt times; asserts the configured number of attempts, the interval within the bound the Kafka test uses, and one message in the dead-letter queue; plus the existing RabbitMQ failure tests stay green.
- 15.1: `consumerOpensAStepSpanNamedAfterTheMessageType` moves from the Kafka and RabbitMQ test classes into `MessageBusTests`, so it also runs on the in-memory bus; `InMemoryMessageBusTests.createBus()` uses a traced context (`TestTracing`) instead of `TestTracing.untraced()`.
- extra: none; closing the routing producer is checked by the step review, as the spec says.

Existing tests this breaks
- MessageRouterTests (convention, `firstRecordComponent`), ECommerceRoutesTests line using the convention: remove those cases.
- ECommerceSettingsTests RabbitMQ dead-letter test: assert the interval as the Kafka one does.
- KafkaMessageBusTests that inject a failing routing template through the 7-argument constructor: inject a failing producer factory instead if the constructor changes.
```

---

## Step 7. Names, dead code, test helpers, settings and docs

```text
Use the `implement-step` skill.

What to build
The rest of the spec's "Names and settings", "Dead code deleted", "Docs", "Generated documents" and "Testing Decisions" (test infrastructure), with no behaviour change:
- `PaymentCanceled.voidedAt` becomes `canceledAt`; "authorisation" becomes "authorization" (OrderEvent.java and docs).
- `kurrentdb.connectionstring` stays.
- application.properties: remove `spring.sql.init.platform`, `spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation`, `spring.jpa.generate-ddl`. The CI workflow `.github/workflows/samples_distributed-processes.yml` drops `-Pci=true`. build.gradle: merge the two `configurations {}` blocks and the two test-task blocks. log4j2-test.xml: `logs/test.log` is rewritten on each run (`append="false"`).
- Delete `ShoppingCartId.derivedFrom` (`ECommerceModule` and the module records went in step 3).
- Tests: PurchaseOnKafkaTraceTests and PurchaseOnRabbitMQTraceTests share one purchase helper and use `PurchaseRestBuilder.paid()`; the Kafka and RabbitMQ redelivery tests get one abstract base per pair (OrderSaga, PaymentExternalEvent), as `MessageBusTests` is; `TestTracing` has one exporter per instance; `LgtmApi` throws on HTTP errors other than "not found"; `ApiSpecification` compares strings with `equals`; `TestDatabases.newKafkaContainer()` and the image and timeout constants are used everywhere; one way to count RabbitMQ messages (`RabbitMQQueues.messageCount`), and `RabbitMQQueues.receiveAll` waits with Awaitility until the expected number arrived (fails on timeout instead of returning fewer; callers assert the number anyway); the OTLP and server property blocks become helpers (`TestLgtm`, `TestDatabases`); test names in sentences and `RetryBackOffTest` becomes `RetryBackOffTests`; `KafkaMessageBusTests` "skipped" test name says dead-lettered.
- Docs: README and HOWTO describe the shop's deciders and command handlers instead of aggregates, the type names, the PostgreSQL store and the summaries of both payments and shipments (the README's "The forwarder could load the aggregate" reason is rewritten for immutable entities); README Java blocks are copies of the code, with `// ...` where code is left out; fix the two line anchors (README GuestStayAccountDecider and GuestStayAccountService links); the HOWTO reset note from step 2 also says that stored type names changed, so old KurrentDB data goes with the same `docker compose --profile all down -v`; HOWTO names `playwrightInstallDeps`; the dev-container Kafka note lives in AGENTS.md and README and HOWTO link to it.
- Regenerate the AsyncAPI documents and the EventCatalog copies.

Context
- review/findings.md sections 9, 11, 12 (T5-T35, E6-E9, E26) for file:line of each item.

Similar code
- MessageBusTests and EventStoreTests (abstract base pattern); TestRabbitMQ.busOn.

Blocked by
Step 6.

Acceptance criteria
None (cleanup; the existing tests protect behaviour).

Tests
- No new behaviour. The moved and merged tests keep their assertions. The step review checks the docs against the code (every README Java block is a copy) and that the removed properties and the CI flag had no effect.
- extra: `TestTracingTests.eachInstanceSeesOnlyItsOwnSpans`. `LgtmApi` error handling is checked by the step review.

Existing tests this breaks
- Tests renamed or moved (redelivery tests, RetryBackOffTests); tests that build `PaymentCanceled` with `voidedAt`.
```
