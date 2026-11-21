# Review: duplication, inconsistencies and hacks in samples/distributed-processes

Date: 2026-11-21. Scope: the code in commit `4ef4a2d3` ("Added real stores, messaging tools to EDA workshop"). Four read-only reviewers, one per area: S = event stores, serialization, subscriptions, checkpoints; M = messaging; E = e-commerce modules; T = tests, build, config, docs. The coordinator re-checked every finding marked "checked" against the code; the rest are reported with file and line, not re-checked.

Paths: `main/` = `src/main/java/io/eventdriven/distributedprocesses/`, `test/` = `src/test/java/io/eventdriven/distributedprocesses/`.

"Agreed" marks a finding that would change a decision in `plans/message-broker/spec.md`, `qa.md` or `plans/saga-and-outbox/plan.md`. Those need Oskar's decision before any change.

## References

- Workshop store: `workshops/build-your-own-event-store/solved/src/test/java/io/eventdriven/buildyourowneventstore/e04_event_store_methods/postgresql/PostgreSQLEventStore.java` (SQL function `append_event`: `FOR UPDATE`, update the stream row, one `INSERT ... SELECT FROM unnest(...) WITH ORDINALITY`; tables `streams` with `type`, `events`). It has a bug: it adds 1 to `stream_position` whatever the number of events.
- Emmett PostgreSQL store (`event-driven-io/emmett`, `src/packages/emmett-postgresql/src/eventStore/schema/`): `appendToStream.ts` (function `emt_append_to_stream`: optimistic `UPDATE emt_streams SET stream_position = ... WHERE stream_position = expected` first, then one `INSERT ... SELECT FROM unnest(...) RETURNING global_position`, one `transaction_id xid8` per append), `tables.ts` (`emt_streams` with `stream_type`, `emt_messages` with `message_kind`, `message_type`, `message_schema_version`, `transaction_id XID8`; processors table with `last_processed_transaction_id XID8`), `readMessagesBatch.ts` (poll: `transaction_id < pg_snapshot_xmin(pg_current_snapshot())`, `(transaction_id, global_position) > (?, ?)`, no type filter).
- Emmett message bus (`src/packages/emmett/src/messageBus/index.ts`): `send` for commands (one handler, fails with none), `publish` for events; messages carry `kind`. Almanac (`src/packages/almanac/src/scopes/scope.ts`): one `scope(name, fn, {parent, context})`.
- Other samples' KurrentDB code: `samples/event-sourcing-kurrentdb-aggregates/src/main/java/io/eventdriven/ecommerce/core/` (`AggregateStore`, `subscriptions/KurrentDBSubscriptionToAll`, `KurrentDBSubscriptionToAllOptions`, `SubscriptionCheckpointRepository`, `CheckpointStored`).
- EDA workshop messaging: `workshops/event-driven-architecture/solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses/` (`core/`, `sagas_kafka/core/versioning/` with one `MessageMapping` bean per module merged by `MessageMappingRegistry`, `sagas_rabbitmq/core/RabbitConfig.java` with Spring AMQP listener retry, `processmanagers/core/AbstractProcessManager` with `Send` and `Publish`).

## 1. Bugs

- **T1 (high, checked). The running application has no OpenTelemetry tracing bridge.** `build.gradle:86` adds `io.micrometer:micrometer-tracing-bridge-otel` as `testImplementation` only; `./gradlew dependencies --configuration runtimeClasspath` lists no `bridge-otel`, the test classpath does. In `bootRun`, `MessageContext` most likely gets a no-op tracer: no traces in Tempo, no baggage, so `correlationId` and `causationId` don't travel on Kafka. Tests hide it because they add the bridge. Not seen in a running app yet. Recommendation: move it to `implementation`, and add a check that runs on the main runtime classpath (for example, a test that asserts the tracer bean is the OpenTelemetry one without test-only dependencies, or a `bootRun` smoke check).
- **E1 (high, checked). The order doesn't check which payment failed.** `orders/OrderCommand.java:41` `RecordOrderPaymentFailure(OrderId orderId, OffsetDateTime failedAt)` has no payment id; `OrderSaga` drops `event.paymentId()`; `Order.recordPaymentFailure` checks only the state (`Order.java:123`), while the authorization checks `paymentId.equals(this.paymentId)` (`Order.java:82`). A late or repeated `PaymentFailed` of payment 1 during the retry with payment 2 records a failure against payment 2: the order fails and payment 2's hold is never captured or canceled, or a confirmed order is canceled without `CancelPayment`. No test covers it. Recommendation: test first, add `PaymentId` to `RecordOrderPaymentFailure` (and `RecordOrderPaymentCapture`), ignore other payments. Needs E16 so the order knows the first payment id.
- **S3 (high, checked). An unreadable stored event is skipped, and the aggregate gets the wrong version.** `KurrentDBEventStore.java:35-39` and `PostgreSQLEventStore.java:223-225` drop events whose type can't be resolved; `AggregateStore.java:111` sets `version = events.size() - 1`. With one skipped event, every append conflicts three times and the API answers 409 for good. In-memory throws instead. Recommendation: take the version from the last event's `streamPosition`, and fail the read (`UnreadableMessageException`). `AggregateStoreTests.versionAfterReplayEqualsEventCountMinusOne` guards the current rule; changing it needs Oskar's OK.
- **S11 (medium-high, checked). `InMemoryEventStore` is not thread-safe but runs in the `in-memory` profile.** `InMemoryEventStore.java:30-35`: `HashMap`, `ArrayList`, a `boolean dispatching`, no lock; check-then-append isn't atomic. Tomcat threads and three `@Scheduled` workers use it. Two appends with the same expected revision can both succeed. Recommendation: one lock around `append`, `subscribe`, `use` and dispatch.
- **E2 (medium, checked by reading, not run). The shop's checkout can end on a 404.** `shop/ShopPages.java:183` redirects to the order page right after checkout; `statusOf` (`:226-231`) throws 404 while `OrderStatuses` has no row. With the Kafka default, the order is placed asynchronously. Recommendation: the order page shows "placing your order" and refreshes when the row is missing.
- **E4 / S26 (medium, checked). `EntityStore` fails on any existing stream.** `core/entities/EntityStore.java:86-95` uses one `Stream` twice (`toList()`, then `reduce`), which throws `IllegalStateException`. Used only by the hotel management code (`GuestStayAccountService`, `GroupCheckoutService`), which the README links. No test covers it. Recommendation: read the list once; decide the hotel code's future under F1.
- **E3 (low-medium, checked). `RandomProductPriceCalculator` isn't thread-safe.** `shoppingcarts/pricing/RandomProductPriceCalculator.java:11` `HashMap` with `computeIfAbsent` from web threads on a singleton. Recommendation: `ConcurrentHashMap` and one `Random`.
- **S25 (medium). `KurrentDBCommandBus` and `KurrentDBEventBus` can't work.** Every publish after the first returns `StreamAlreadyExists` (they append with `noStream` to one stream) and is dropped silently; they store the envelope class as the event. Supports F1 option A (delete them).
- **M13 (medium, checked). The dead-letter counter tags a different destination per broker.** Kafka tags the topic (`KafkaMessageBus.java:308`), RabbitMQ the queue (`RabbitMQMessageBus.java:256`). Recommendation: tag the queue on Kafka too.
- **M16 (medium). `bind` declares the channel on RabbitMQ only.** Kafka's `bind` doesn't create the channel topic; it works only because `OrdersConfig.configureProcess` runs last (`ECommerceConfig.java:80`). Recommendation: one rule on all buses.
- **T2 (medium, checked). Grafana and EventCatalog both use port 3000.** `docker-compose.yml:126`, `eventcatalog/playwright.config.js:9,16` (`reuseExistingServer` outside CI), `HOWTO.md:161,175`. With the default stack running, a local `eventCatalogTest` tests Grafana. Recommendation: move the catalog to another port.
- **T3 / S29 (medium). `ShoppingCartTests` can't fail.** `test/shoppingcarts/ShoppingCartTests.java:40-49` asserts only inside `catch`; no failure if the second append succeeds. It tests the KurrentDB client, not sample code, and never closes the client. Recommendation: delete it (the shared store tests cover `noStream`) or rewrite with `assertThatThrownBy`.
- **E5 (low). Nested retry in the hotel code.** `hotelmanagement/saga/groupcheckout/GroupCheckoutService.java:34,38`: up to N×N attempts.
- **M19 (low). Kafka routing producer is never closed.** `KafkaMessageBus.java:117-132` creates a second producer factory; `close()` destroys only the first.
- **M23 (low, latent).** In a `continuing` scope with no baggage, published message ids derive from the `correlationId`, so two steps could produce equal ids. Recommendation: derive only from a real handled message id.
- **M4 (low).** The derived `messageId` uses the Java class name (`MessageContext.java:113-117`), so a renamed class changes ids of redelivered messages. Use the stable type name.

## 2. PostgreSQL event store

- **S7 (high, checked; agreed: spec r23 line 271 says "copied from the build-your-own-event-store workshop's `PostgreSQLEventStore`").** The store is not a copy. `PostgreSQLEventStore.java:150,191-204` inserts a `-1` row, locks it with `SELECT ... FOR UPDATE` and compares in Java; `:161-179` one `INSERT` per event; `:181-185` `update streams` after the inserts; `:42-63` no stream type, no message kind, `transaction_id bigint` from `pg_current_xact_id()::text::bigint`, positions 0-based with `-1` for "no stream". About 3 + N round trips per append, against 1 in the workshop and in Emmett.

| | Workshop | Emmett | Now |
|---|---|---|---|
| Append | SQL function | SQL function | JDBC from Java |
| Check | `FOR UPDATE`, compare | optimistic `UPDATE ... WHERE stream_position = expected` | upsert, `FOR UPDATE`, compare in Java |
| Stream row update | before insert | first, as the check | after the inserts |
| Insert | one `unnest` insert | one `unnest` insert | one per event |
| Tables | `streams` (+`type`), `events` | `emt_streams` (+`stream_type`), `emt_messages` (+`message_kind`) | `streams`, `events` |
| `transaction_id` | none | `xid8` | `bigint` |

Recommendation: an `append_to_stream` SQL function in Emmett's shape (optimistic update first, then one `unnest` insert; fixes the workshop's +1 bug), tables `streams` (with `stream_type`) and `messages` (with `message_kind` and `message_type`), `transaction_id xid8`. Names must not refer to Emmett. The event store tables share the projections database (`application.properties:5,14`), so consider a schema or prefix. Changes the spec's "copied from the workshop" wording.
- **S8 (medium, checked). A subscription with rare types rescans the log on every poll.** `PostgreSQLSubscription.java:23-31` filters `type = any (?)` in SQL and moves the cursor only on handled rows (`:82`), so every 100 ms poll rereads later rows of other types. Emmett reads all messages and moves the cursor over the batch. Recommendation: no type filter in SQL, filter in Java, move the in-memory cursor over the batch; store checkpoints only for handled events.
- **S9 (low).** All PostgreSQL subscriptions run in turn on one thread (`PostgreSQLEventStore.java:131-133` `synchronized poll()`). Fine for a sample; document it.
- **S10 (low).** `PostgreSQLSubscription.java:61` reads the checkpoint in the constructor, so the app start fails if the projections database is down; `poll` loads it lazily anyway. Drop the eager read.

## 3. Event store contract

- **S1 (high, checked). `EventStore` exposes KurrentDB client types.** `core/kurrentdb/EventStore.java:6-7,37,41,55`: `StreamState`, `Position` in `AppendResult` and `DeleteResult`. PostgreSQL and in-memory build fake `Position`s (`PostgreSQLEventStore.java:188`, `InMemoryEventStore.java:83`) and use KurrentDB revisions; the PostgreSQL test queries `transaction_id` by hand because the result can't carry it (`PostgreSQLEventStoreTests.java:52-55`). Recommendation: the store's own types: `ExpectedStreamVersion` (`noStream`, `any`, `streamExists`, `at(n)`), `long streamVersion`, a global position or checkpoint that carries the transaction id when the store has one. Only `KurrentDBEventStore` maps to the client types. Changes the shared store tests.
- **S2 (medium).** Read results differ per store: `NoEventsFound` only in memory (`InMemoryEventStore.java:47`), PostgreSQL returns `StreamDoesNotExist` when no row could be read, KurrentDB returns `Success` with what it could read. Recommendation: delete `NoEventsFound`; define read once in the shared `EventStoreTests`.
- **S4 (medium).** An unreadable event is skipped in store subscriptions (`EventSerializer.java:112-118` warns, `KurrentDBEventStore.java:126` `.ifPresent`) but dead-lettered on the buses. Later events move the checkpoint past it. Recommendation: treat it as a handler failure (retry with back-off).
- **S5 (low).** `continueTraceOf` is a tracing concern on the store interface (`EventStore.java:20`), the same one-liner in all three stores, one caller (`AggregateStore.java:47`). Recommendation: `AggregateStore` takes `MessageContext`.
- **S28 (medium; agreed: spec line 309 asks for one shared test set).** Store-neutral tests still live only in `InMemoryEventStoreTests` (`readOnUnknownStream...`, `appendWithNoStreamOnExistingStream...`, `appendWithAnyRevision...`, `appendWithStreamExists...`, `appendReturnsRevision...`, `notifiesByExactRuntimeClass...`, `failedAppendNotifiesNobody`); `EventStoreTests` has 8. The append and read rules aren't checked on KurrentDB or PostgreSQL. Recommendation: move them, as the spec says.
- **S27 (low).** `AggregateStore.java:64` builds the ETag from `StreamState.toString()`; version conflicts use the JDK's `ConcurrentModificationException` (`AggregateStore.java:66-71`, `ApiExceptionHandler.java:14`). Recommendation: a long version and a dedicated conflict exception.
- **S13 (low).** In memory, a middleware is a fake subscription named `"middleware"` with `null` types (`InMemoryEventStore.java:111-115`): failures are logged as a subscriber's and retried. The bus lets them propagate. Recommendation: a plain list, as in `InMemoryMessageBus`.

## 4. Serialization and type names

- **S14 / M1 (high, checked). Four type-name formats and two serializers.** Bus: registered names (`ECommerceMessages.java:14-50`, `payments.AuthorizePayment`) through `messaging/versioning/MessageSerializer` and `MessageMapping`. Bus read fallback: the Java class name inside the trusted package (`MessageSerializer.java:46-58`). KurrentDB and PostgreSQL: `getTypeName().replace("$","__").replace(".","_")` through the static `serialization/EventTypeMapper` and `EventSerializer` (KurrentDB client types). In-memory: `getTypeName()` (`InMemoryEventStore.java:104`). `EventTypeMapper.toClass` turns every `_` into `.` (breaks for any `_` in a name) and calls `Class.forName` on database values with no trusted-package check (spec line 251: "Only classes in the sample's package are trusted"). The same event has one name in the store and another on the bus, and only the bus can read old versions. Recommendation (Oskar's request): one `MessageTypeMapper` (registered name to class, transformers for old names) and one `MessageSerializer`, used by the three stores and three buses, for events and commands. Delete `EventTypeMapper` and `EventSerializer`. Stored names change: either register old names as aliases or reset dev data (decision).
- **S12 (medium).** The in-memory store has its own `JsonMapper` with other settings (`InMemoryEventStore.java:235-240`: field visibility ANY, `ADJUST_DATES_TO_CONTEXT_TIME_ZONE=false`), so an `OffsetDateTime` keeps its offset in memory but comes back as UTC from KurrentDB and PostgreSQL. Business tests run in memory. Recommendation: one serializer.
- **S15 (medium).** The buses use the store's static `EventSerializer.mapper` (`ECommerceApplicationConfig.java:166-170,184-188`, the same `MessageSerializer` built twice; `test/testing/TestMessages.java:12`). Recommendation: one `MessageSerializer` bean.
- **S16 (low; key names agreed, spec line 61).** Metadata is written in `EventSerializer.java:47-52` and `PostgreSQLEventStore.java:212-220`, read in `KurrentDBEventStore.java:220-235` and `PostgreSQLEventStore.java:227-245`. Recommendation: one metadata codec with the plain and `$` key sets; the names don't change.
- **M2 (medium).** `MessageMapping.register` overwrites silently (`MessageMapping.java:14-18`). With convention-based names, the five clashes would map one name to the last class. Recommendation: fail at startup on a duplicate name or class.
- **M3 (low; agreed: spec line 251, trusted package).** The class-name read fallback is used only by `MessageSerializerTests.aClassNameInsideTheTrustedPackageIsRead`; production never writes class names; `isRegistered` tags such names `unknown`. Recommendation: read only mapped names, so the mapping is the allow-list.
- **M5 (low).** The workshop has one mapping per module, merged by `MessageMappingRegistry`; the sample has one central `ECommerceMessages`. Recommendation: each module registers its own types.
- **EventSerializer bug (low, checked).** `serialize(CommandEnvelope)` maps `commandEnvelope.getClass()`, so every command gets the envelope's type name. Only the unused `KurrentDBCommandBus` calls it.

## 5. Convention-based registration of type names and routes

Oskar asked for it. The current state and the blockers:

- 32 type names are registered by hand as `module.SimpleName` (`ECommerceMessages.java:14-50`).
- Five simple names repeat inside one module: `PaymentEvent.PaymentAuthorized` / `PaymentExternalEvent.PaymentAuthorized`, `PaymentCaptured`, `StockReserved`, `PackageWasSent`, `ShoppingCartCheckedOut`. Each pair is a stored event and its published twin.
- The published module name `shopping_carts` differs from the package `shoppingcarts` (`ShoppingCartsConfig.java:23`, `OrdersConfig.java:37`, `ECommerceMessages.java:48`, and the special case in `ModuleOperations.java:30`). `"ecommerce"` repeats in four `*Id.of` and three controller call sites (E13).
- Three registries are kept by hand: `ECommerceMessages` (type names), `MessageBusClassScanner.java:22-29` (Springwolf), `EventCatalogMessageTypes.java:21-22` (already uses `getPermittedSubclasses()`) (E13).
- `ShipmentCommand` has no common id; `ReserveStock(String referenceId, ...)` lets the facade derive the shipment id, so shipments need six `routeKey` lines and `ReserveStock` gets the order's key (E10; agreed: spec r15 "the `referenceId` for `ReserveStock`").
- `ShoppingCartExternalEvent` declares no id accessor and is routed per record; payments and shipments route their external interface by `referenceId()` (E12).
- Event interfaces declare no id, so every projection repeats a `getDocumentId` switch with one case per event (E11: `CustomerOrders.java:35-46` 9 cases, `OrderStatuses.java:37-51` 12 cases, and eight more views).
- `CreateCheckoutSession`, `RetryOrderPayment` and `CancelOrder` are registered and routed but never travel on the bus; the API calls the facades (E14, M27; agreed: saga-and-outbox `plan.md:370,381` "leave the message bus"). Registering every member of a sealed interface would add them back.

Routing findings:

- **M8 (medium, checked). Routing depends on registration order and a package-wide catch-all.** `MessageRouter.java:20-31` takes the first non-null target and key in registration order. `PaymentsConfig.java:56-59` registers `PaymentExternalEvent` before `inPackage(payments)`; swapping the lines sends external events to `payments-internal` and the saga never gets them. `inPackage` also routes any message of the package, so a mistaken publish doesn't fail (Q20: "publish fails for a message with no route"; Q26: "register all routings and have explicit routing and intentional policy").
- **M6 (medium, checked; agreed with spec "no reflection").** `MessageRouter.convention` and `firstRecordComponent` (reflection) are passed as `message -> new RoutingTarget(null, null)` by all three buses and used only by tests. Delete them.
- **M7 (low).** `MessageRoutes.Route` duplicates `RoutingTarget`. **M9 (low).** `MessageRoutes` has five methods for one idea; callers pass functions that ignore their input (`routeTarget(AuthorizePayment.class, command -> requests)`).
- **M11 (medium).** Module and channel names: `shoppingcarts` / `shopping_carts-external` / `shopping-carts-external-forwarder` / `shopping-cart-summaries`; payments publishes to `responses`, carts to `external`; queue names follow two rules (`payments-requests` for a module's own queue, `orders-<channel>` for the saga's).

Recommendation (to settle in brainstorm): the module name is given once per module config; each module registers its sealed interfaces, and type names follow `<module>[.<segment>].<SimpleName>` with explicit overrides and a startup failure on a clash (a `stored` segment, or similar, resolves the five clashes and keeps the published names); routes stay one visible line per sealed interface, with record-level overrides that win by specificity, not order; startup checks that every permitted subclass of a routed interface has a channel and key; no `inPackage`, no reflection. Prerequisites: E10, E11, E12, E14. Sketch from the messaging review:

```java
public static final MessageModule module = MessageModule.named("payments");

types.in(module)
    .names(PaymentCommand.class)
    .names(PaymentExternalEvent.class)
    .names(PaymentEvent.class, "stored")
    .upcast("payments.PaymentAuthorized.v1", PaymentAuthorizedV1.class, PaymentAuthorizedV1::toCurrent);

new MessageRoutes()
    .route(PaymentCommand.class, module.channel("internal"), command -> Urns.routingKeyOf(command.paymentId().value()))
    .route(AuthorizePayment.class, module.channel("requests"))
    .route(PaymentExternalEvent.class, module.channel("responses"), event -> Urns.routingKeyOf(event.referenceId()));
```

## 6. Message buses

- **M12 (medium).** Kafka and RabbitMQ copy about 90 lines (routes, subscribe, `use`, publish prelude, prepare-then-send, consumer dispatch, failure helpers, constants, two different `FailureHandling` records); the in-memory bus copies about 30. The copies have drifted (M13, M14, M15). Recommendation: two small concrete classes (`QueueSubscribers`, `PublishRoutes`) and one constants class for header and metric names. AGENTS.md: no premature abstraction, so this needs Oskar's OK.
- **M14 (medium, checked; agreed: spec "The in-memory bus carries the five values too, so all buses behave the same").** The in-memory bus opens no step span (`InMemoryMessageBus.java:42` `context.handling`; Kafka and RabbitMQ use `consuming`). Recommendation: use `consuming`; move the span test into `MessageBusTests`.
- **M15 (low).** In-memory keys handlers by type name string, Kafka and RabbitMQ by `Class`.
- **M17 (medium, checked; outcome agreed: spec criterion 9).** RabbitMQ decides `mandatory` from the channel name suffix (`RabbitMQMessageBus.java:45,373` `Set.of("-requests", "-internal")`). Recommendation: say it explicitly, for example by message kind (M24) or on the channel declaration.
- **M18 (medium; agreed: spec "The brokers use the workshop's `MessageFailureStrategy` and `RetryBackOff`").** RabbitMQ retries in a hand-written `while (true)` with `Thread.sleep` (`RabbitMQMessageBus.java:205-219,277-284`); the workshop uses Spring AMQP's listener retry with a recoverer; Kafka uses `DefaultErrorHandler`. Recommendation: use the library retry on RabbitMQ too.
- **M24 (medium; agreed: spec "They still call `routes`, `bind`, `subscribe` and `publish`").** No `send`/`publish` split and no message kind. Emmett and the workshop's process manager separate them. Recommendation: `send` for commands (one subscriber, fails with none) next to `publish`, or the kind from registration.
- **M20 (low).** The two bus constructors have different shapes; RabbitMQ needs `ApplicationContext` because spring-rabbit 4.1.2 has no `setObservationRegistry` (not a hack). **M28 (low).** `MessageBus.use` is used only by tests (it comes from the workshop). Lower notes: Kafka routing consumer retries forever with doubled logs; RabbitMQ starts a container inside `computeIfAbsent`; both buses read the message again only to pass it to a strategy that ignores it.
- **M26 (low, verify).** Spring Boot also auto-configures Kafka and RabbitMQ (`build.gradle:43,46`, nothing excluded), so in Kafka mode Boot probably builds a RabbitMQ connection factory and health indicator; `/actuator/health` is likely DOWN. Not checked in a running app.

## 7. Metadata and context

- **M21 (medium).** Two `EventEnvelope` and two `EventMetadata` records (`core/events` with `$`-named fields, used by `EventSerializer` and `KurrentDBEventBus`; `core/messaging`, used by the stores). `MessageValues` repeats five of `EventMetadata`'s eight fields. Recommendation: delete the `core/events` pair (with F1 and the hotel code); `EventMetadata` holds the message metadata plus the stream position.
- **M22 (medium; ID rules agreed: spec line 257).** `MessageContext` has seven entry points with overlapping names (`inherit`, `inheritForPublished`, two `handling` overloads that do different things, `continuing`, `consuming`, `publishing`). Recommendation: one `scope(stepName, inheritedFrom)` with three sources (received message, stored event, nothing), as Almanac's one `scope`.

## 8. Subscriptions and checkpoints

- **S18 (medium).** Four checkpoint classes (`core/projections/Checkpoint`, `ProcessorCheckpoint`, `ProcessorCheckpointRepository`, `ProcessorCheckpoints`), in the projections package, so the stores depend on projections. Words in use: subscription, processor, handler with ack. The other samples: `SubscriptionCheckpointRepository`, `KurrentDBSubscriptionToAll`. Recommendation: package `core/subscriptions`, names `SubscriptionCheckpoint`, `SubscriptionCheckpointRepository`, `SubscriptionCheckpoints`, `Checkpoint`; keep the agreed table and columns (spec line 273: `processor_checkpoints`, `processor_id`).
- **Naming (checked).** `AllStreamSubscription` and `SubscribeToAllOptionsFactory` against the other samples' `KurrentDBSubscriptionToAll` and `KurrentDBSubscriptionToAllOptions`.
- **S19 (medium-low).** KurrentDB fills the transaction id with 0 (`ProcessorCheckpoints.java:28-30`). With a shared projections database, switching from `postgresql` to `kurrentdb` leaves a stored transaction id > 0, so every KurrentDB event counts as handled and is skipped forever. `ProcessorCheckpoint.java:18` `columnDefinition = "bigint not null default 0"` is a migration workaround. Recommendation: each store gives each event its checkpoint; document that a projections database belongs to one store type.
- **S21 (low-medium).** Back-off restart, `subscribe` checks, the `handle %s` wrap and `matches(expected, actual)` are repeated in each store. Recommendation: small helpers; ask before a base class.
- **S22 (low).** `RetryBackOff.exponential` sets `setMaxAttempts(2)` and subscriptions restart it on `STOP`, so delays cycle 1 s, 2 s, 1 s and never grow. Stores hard-code 1 s and a 100 ms poll; the bus reads `messaging.retry.initial-interval`.
- **S23 (low).** Two transaction boundaries for a projection: `ProjectionRepository.inTransaction` (`@Transactional` default method) inside the `ProcessorCheckpoints` `TransactionTemplate`.
- **S20 (info; agreed: spec line 275, criterion 27.5).** The checkpoint check runs after the handler work and rolls back. No change.

## 9. Packages and dead code

- **S6 (checked).** `core/kurrentdb` holds `EventStore`, `InMemoryEventStore`, `PostgreSQLEventStore`, `PostgreSQLSubscription`. Recommendation: `core/eventstore`, `core/eventstore/kurrentdb`, `core/eventstore/postgresql`.
- **S24 / M27 (checked).** No users: `core/processing/HandlerWithAck`, `HandlerWithAckProcessor`, `core/async/SyncProcessor` (copied from `samples/uniqueness`), `core/commands/CommandHandler` (with its reflection hack), `core/kurrentdb/subscriptions/SubscribeToAllOptionsFactory` (it also filters by stream prefix built from a type name), `KurrentDBEventStore.deleteStream` ×2 and `setStreamMaxAge`, `EventStore.DeleteResult`, `KurrentDBCommandBus`, `KurrentDBEventBus` (F1). Recommendation: delete.
- **E26 (low).** `ShoppingCartId.derivedFrom`, `ECommerceModule.shipments()`/`ShipmentsModule` unused; `*Id.of(UUID)` used only by tests; `FulfillmentService.java:8-9` comment describes what only the test fake does.
- **H1 (note).** The hotel management code shares nothing with e-commerce, relies on the older core (`CommandBus`, `EventBus`, `EntityStore`, `AbstractProcessManager`), uses UUID ids, services and deciders, and only `saga/gueststayaccount` has tests. Decision needed together with F1.

## 10. E-commerce modules

- **E15 (medium, decided in qa.md Q8: both modules follow payments' way; only the request event and the external events carry `referenceId`).** Payments and shipments answer "who asked?" differently: payment events lose `referenceId`, so `PaymentExternalEventForwarder.java:98-104` reads it from `PaymentSummaries` and throws until that view catches up; shipment events carry `referenceId`. Recommendation: one design; payment events carrying `referenceId` would remove `PaymentSummaries`.
- **E16 (medium; agreed: `plan.md:420`).** The payment id rule is written in three places (`OrderSaga.java:69`, `OrderStatuses.java:64`, `Order.java:141`), and `Order.paymentId` is null until the first authorization. Recommendation: `Order.evolve(OrderPlaced)` sets the first payment id.
- **E22 (medium, checked).** The order reads shipment state from a cancellation reason (`Order.java:249-251`). Recommendation: `OrderShipmentFailed` carries the shipment's reason; map to a cancellation reason only when canceling.
- **E23 (low-medium).** `FulfillmentServiceClient` logs and swallows a failed call (order stalls for 7 days), while `CaptureGatewayClient` records a failure. Decide one rule.
- **E24 (low).** `OrdersAwaitingPayment` keeps a row after a retried payment is authorized, so the worker sends a no-op `CloseUnpaidOrder` every minute.
- **E6 (checked; agreed rename in saga-and-outbox `plan.md:228`).** `PaymentCanceled(PaymentId, OffsetDateTime voidedAt)` kept the old word. Rename to `canceledAt`. **E7.** "authorisation" (`OrderEvent.java:51`) against "American spelling everywhere". **E8.** "Out of stock" has four spellings; shipment events mix `PackageWasSent` and `StockReserved`; reason enums sit in different places. **E9.** Shopping carts name facade methods, visibility, status enums and id fields differently from the other modules (`cartId` vs `shoppingCartId`, URN type `cart`).
- **E17, E18, E19, E20, E21, E25 (low).** Duplicate `statusOf` in `OrdersController` and `ShopPages`, URNs built by hand; `Carts` and `ShoppingCartSummaries` nearly equal and two progress rules (`CustomerOrders.evolve`, `OrderStatus.refreshed`) that can drift (views per page are agreed, `plan.md:413`); the same `MessageSerializer` built twice, `PricingConfig` outside the central wiring, `ECommerceConfig.configure` with 19 parameters; a repeated `@AsyncPublisher` block; time taken from different places (`CloseUnpaidOrder` has no time; `now` first in `Payment.confirmAuthorization`); untyped `new ArrayList<>()` in `OrderSaga.java:222`; `orElseThrow()` 404 with "No value present".

## 11. Tests

- **T4 (medium).** The RabbitMQ dead-letter test's name promises the configured interval but checks only the count (`ECommerceSettingsTests.java:159-191`); the Kafka twin checks timing.
- **T8, T9, T10, T11, T12 (medium-low).** Copied test code: the two purchase trace tests and `GrafanaExportTests` share helpers and the purchase block; three classes pay by hand although `PurchaseRestBuilder.paid()` exists; the Kafka and RabbitMQ redelivery tests have no shared base; the OTLP and server property blocks are copied. **M29.** Six scenarios are written twice in `KafkaMessageBusTests` and `RabbitMQMessageBusTests` (spec: one shared test set).
- **T5, T6 (low; T5 accepted in step 14 review).** The Loki test checks a line the test writes; a success logged at ERROR. **T7.** A 7 s wait tied to a 5000 ms setting with no shared constant. **T16.** `LgtmApi` turns HTTP errors into empty results, so a bad query waits 90 s. **T17.** `TestTracing` shares one static exporter across all tests. **T18.** `ApiSpecification.java:50,77` compares strings with `!=`. **T19.** The two `ProjectionsDatabase` contexts use different annotations. **T13, T14, T15.** Image tags and timeouts defined in several places; two ways to count RabbitMQ messages. **T20 / M30.** Three test-name styles; `RetryBackOffTest` (singular); a Kafka test name says "skipped" for a dead-lettered record.
- **T21 (note; agreed in message-broker step 1).** Tests filter messages by `toString().contains(...)`.

## 12. Build, configuration and docs

- **T30 (medium, checked; contradicts the agreed two-flow design, spec r22).** `HOWTO.md:177` and `README.md:741` say to search Tempo by `correlationId` with the order id, and that a purchase is one trace. The `correlationId` is a random id and a purchase forms two flows split at "Pay now".
- **T22 (checked).** CI passes `-Pci=true`, which this build never reads. **T23.** The CI dependency step retries with `--refresh-dependencies`; `eventCatalogTest` runs once per JDK. **T24.** `spring.sql.init.platform`, `hibernate.jdbc.lob.non_contextual_creation` have no effect; `spring.jpa.generate-ddl` repeats `ddl-auto`. **T25 / M25.** `kurrentdb.connectionstring` is not under `eventstore.*`; two `publish-timeout` settings; the bus factory takes 14 parameters including RabbitMQ credentials in Kafka mode (the workshop used `@ConfigurationProperties` records). **T27.** Two `configurations {}` and two test-task blocks in `build.gradle`. **T28.** `logs/test.log` appends forever (42 MB now). **T29.** The dashboard's RabbitMQ queries are never tested.
- **T26 (agreed: spec line 215).** RabbitMQ `bootRun` also starts Kafka (`sample` profile); mention it in the HOWTO.
- **T31, T32, T33, T34, T35 (low).** Two README line anchors point to wrong lines (`README.md:162,172`); README blocks at `398-408` (OrderSaga, an annotation missing) and `539-562` (PaymentSummaries, constructor missing, doesn't compile) are not exact copies; `README.md:705` describes the `api` test folder too simply; `HOWTO.md:45` is an upgrade note in a first-run guide, `HOWTO.md:189` misses `playwrightInstallDeps`; the dev-container Kafka warning is written three times.

## Vocabulary proposed by the reviewers

- Packages: `core/eventstore` (+ `kurrentdb`, `postgresql`), `core/subscriptions`, `core/serialization` (`MessageTypeMapper`, `MessageSerializer`, `MessageTransformer`, metadata codec), `core/messaging`.
- Store reader: "subscription" everywhere; "processor" only in the agreed `processor_checkpoints` table. Drop "handler with ack".
- Store types: `ExpectedStreamVersion`, `streamVersion`, `Checkpoint(transactionId, globalPosition)`; `AppendResult.{Success, StreamAlreadyExists, Conflict, UnexpectedFailure}`, `ReadResult.{Success, StreamDoesNotExist, UnexpectedFailure}`.
- Messaging: message (command or event, with a kind), type name, module, channel (`<module>-requests|responses|internal|external`), routing key, route (channel, key; replaces `RoutingTarget` and `MessageRoutes.Route`), binding pattern, queue (`<consumer module>-<channel>`), subscriber, step, message metadata (today `MessageValues`), event metadata.
- Headers and metrics in one constants class: `messageType`, `messageId` (a header on both brokers, as in the workshop); `messaging.dead_lettered` tagged with the queue.

## Decisions for Oskar

These change agreed decisions or add abstractions, so the brainstorm must settle them:

1. PostgreSQL store close to Emmett's shape instead of "copied from the workshop" (S7), with `messages`, `message_kind`, `stream_type`, `xid8`.
2. The `EventStore` interface gets its own types instead of the KurrentDB client types (S1), and the version comes from the last event (S3, changes `versionAfterReplayEqualsEventCountMinusOne`).
3. One type mapper and serializer for stores and buses, events and commands (S14); old stored names as aliases or a data reset.
4. Convention-based type names and routes (section 5), with E10 (`ReserveStock` gets a shipment id, against spec r15), E11, E12, E14.
5. `send` and `publish`, and a message kind (M24, against "they still call ... `publish`").
6. Shared bus pieces (M12) and store helpers (S21) against "no premature abstraction".
7. RabbitMQ retry through Spring AMQP (M18).
8. F1 and the hotel management code (H1, S25, E4).
9. One "who asked?" design for payments and shipments (E15).
10. Scope and order: the bugs in section 1 first, or all in one spec.
