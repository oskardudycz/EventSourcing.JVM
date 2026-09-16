# Implementation plan — ECommerce distributed process

Blueprint for building what [spec.md](spec.md) describes, broken into TDD-sized steps with a
ready-to-use prompt per step. State lives in [todo.md](todo.md); the decisions and their reasoning
live in [qa.md](qa.md).

**Module:** `samples/distributed-processes` (standalone Gradle project, Java 22)
**Verification after every step:** `./gradlew build` from `samples/distributed-processes`. Compile,
linter and the full test suite must be green before the next step starts. Test output must be clean
— no stack traces on a passing run.

---

## 0. What the code actually looks like

Facts worth knowing before the first prompt. `spec.md` has been corrected to match all of these, so
the two documents agree; they are repeated here because each prompt depends on them.

- **`EventSourcedSpecification` cannot test these aggregates.** It is decider-shaped — `Supplier<Entity>`
  plus `BiFunction<Entity, Event, Entity> evolve`, with `when` as `Function<Entity, Event[]>` — while
  the ecommerce aggregates are mutable `AbstractAggregate`s with `void when(Event)` and `enqueue`.
  `hotelmanagement`'s test depends on its current shape, so leave it and add a sibling.
- **`EventStore.read` has zero callers** and `ReadResult` is referenced nowhere, so
  `ReadResult.Success` can stop carrying the ESDB-only `ResolvedEvent[]`.
- **`AggregateStore` is never constructed anywhere** — it appears only as a declared field type, in
  `ShoppingCartService`, the two forwarders and `hotelmanagement`'s `GuestStayAccountService`. Its
  constructor and internals are free.
- **The in-memory store is modelled on the introduction-to-event-sourcing workshop**, specifically
  `workshops/introduction-to-event-sourcing/solved/src/test/java/io/eventdriven/introductiontoeventsourcing/e13_entities_definition/core/EventStore.java`.
  Read it before step 0.2. Appending both persists and publishes; events round-trip through Jackson.
  What we add on top of the workshop version is expected-revision handling, because every
  `ShoppingCartCommand` already carries an `expectedVersion` and `AggregateStore` already returns
  `ETag`s.
- **`OrderEvent.OrderPaymentRecorded` already carries `productItems`**, so the orders forwarder maps
  rather than re-reads. Only the shopping-cart forwarder genuinely enriches. The article's trick
  still lives at the saga, which recovers the items by routing through `RecordOrderPayment`.
- **Nine defects**, listed in spec §11. Each is fixed in the track that owns the file.

---

## 1. Shape of the work

```
Phase 0  core seams                 sequential, 4 steps   — nothing else can start
Phase 1  four modules               4 parallel tracks, 12 steps
Phase 2  saga + composition root    sequential, 2 steps   — needs all of Phase 1
Phase 3  failure paths              3 parallel tracks, 3 steps
Phase 4  documentation              sequential, 1 step
```

Phase 1 tracks touch disjoint packages and share only `core`, frozen by Phase 0. Phase 3 tracks all
add scenarios to `OrderProcessTests`, so run them in parallel but merge one at a time.

| Step | Deliverable | Depends on |
|---|---|---|
| 0.1 | `core/messaging`: bus interfaces + in-memory command and integration buses | — |
| 0.2 | `EventStore` interface, `ESDBEventStore`, `InMemoryEventStore` that publishes on append | 0.1 |
| 0.3 | `AggregateStore` on `EventStore`, version fix | 0.2 |
| 0.4 | Test helpers: `AggregateSpecification`, `MessageCatcher` | 0.3 |
| A.1 | `ShoppingCartFacade` + `ShoppingCartsConfig` | 0.4 |
| A.2 | Cart forwarder, wired | A.1 |
| B.1 | `Order` defects fixed + aggregate unit tests | 0.4 |
| B.2 | `OrderFacade` + `OrdersConfig` | B.1 |
| B.3 | `OrderExternalEvent` + forwarder, wired | B.2 |
| C.1 | `Payment` `mapToStreamId` + aggregate unit tests | 0.4 |
| C.2 | `PaymentFacade` + `PaymentsConfig` | C.1 |
| C.3 | `PaymentGateway` + `PaymentGatewayClient` + test doubles | C.2 |
| C.4 | Payment forwarder, wired | C.2 |
| C.5 | `PendingPayments` + `PaymentTimeoutWorker` | C.4 |
| D.1 | `Shipment` defects fixed, delivery added + aggregate unit tests | 0.4 |
| D.2 | `ShipmentFacade` + `ShipmentsConfig` | D.1 |
| D.3 | `DeliveryProvider` + `DeliveryProviderClient` + test doubles | D.2 |
| D.4 | `ShipmentExternalEvent` + forwarder, wired | D.2 |
| 2.1 | `OrderSaga` rewritten + unit tests | A.2, B.3, C.4, D.4 |
| 2.2 | `ECommerceConfig` + E2E happy path | 2.1, C.3, C.5, D.3 |
| 3.1 | Out-of-stock and payment-rejected scenarios | 2.2 |
| 3.2 | Timeout scenario | 2.2 |
| 3.3 | Manual compensation + unpaid-order scenario | 2.2 |
| 4.1 | README rewrite | 3.1, 3.2, 3.3 |

---

## Phase 0 — Core seams

Sequential. These four steps freeze the foundations every module track builds on.

### Step 0.1 — The messaging abstractions

```text
Work in the Gradle project at samples/distributed-processes (Java 22). Practice TDD: write the
failing test first, run it, then write the minimum code to pass, then refactor.

Goal: add the typed, bare-message channels the ecommerce process will use.

Important constraint: the existing core/commands/CommandBus and core/events/EventBus are
envelope-based (they return EventStore.AppendResult and subscribe with
Consumer<EventEnvelope<Object>>), and hotelmanagement depends on them. Do NOT change or delete
them, and do not touch their ESDB implementations. Add a new package instead.

Create io.eventdriven.distributedprocesses.core.messaging with four interfaces:

  interface EventBus {
    <Event> void publish(Event... events);
    <Event> EventBus subscribe(Class<Event> type, Consumer<Event> handler);
    EventBus use(Consumer<Object> middleware);
  }

  interface CommandBus {
    <Command> void send(Command... commands);
    <Command> CommandBus handle(Class<Command> type, Consumer<Command> handler);
    CommandBus use(Consumer<Object> middleware);
  }

  interface IntegrationEventBus extends EventBus { }

  interface InternalEventBus {          // subscription only — publishing a module's internal
                                        // events is appending them, see step 0.2
    <Event> InternalEventBus subscribe(Class<Event> type, Consumer<Event> handler);
    InternalEventBus use(Consumer<Object> middleware);
  }

Then two implementations, InMemoryEventBus implements IntegrationEventBus and InMemoryCommandBus
implements CommandBus, with these semantics:
- dispatch is synchronous and depth-first: publishing an event runs its handlers to completion,
  including anything they publish or send, before the next event in the same call;
- EventBus allows many handlers per event type, invoked in registration order;
- CommandBus allows exactly ONE handler per command type. A second registration for the same type
  throws IllegalStateException naming the type — that is a wiring mistake, not a runtime condition;
- sending a command with no registered handler throws IllegalStateException naming the type;
- publishing an event with no subscribers is a no-op;
- middleware registered with use() runs for every message, before handlers, in registration order;
- subscribe/handle return this, so wiring reads as a fluent chain;
- dispatch is by exact runtime class, not by assignability — keep it predictable.

Tests in src/test/java/io/eventdriven/distributedprocesses/core/messaging/, one class per bus,
covering every bullet above including both throwing cases and the depth-first ordering (a handler
that publishes a second event must see that event fully handled before it returns).

Constraints: no new dependencies, no Spring, no reflection beyond getClass(). Match the surrounding
code style — two-space indent, var, records, sealed interfaces.

Finish by running ./gradlew build and confirming it is green.
```

### Step 0.2 — `EventStore` interface and an in-memory store that publishes on append

```text
Continue in samples/distributed-processes. TDD as before.

READ THIS FIRST — it is the model for the whole step:
workshops/introduction-to-event-sourcing/solved/src/test/java/io/eventdriven/introductiontoeventsourcing/e13_entities_definition/core/EventStore.java
That store does two things in one call: appendToStream persists the events as (eventType, json)
envelopes AND dispatches them to middleware and typed subscribers. That is the shape we want — it
removes the store-then-publish gap entirely, so a facade that appends has already published.

Goal: turn io.eventdriven.distributedprocesses.core.esdb.EventStore from a concrete ESDB class into
an interface with two implementations, one of which behaves like the workshop store.

Context:
- EventStore is currently a class wrapping EventStoreDBClient. Public surface: read(String),
  append(String, Object...), append(String, ExpectedRevision, Object...), deleteStream(String),
  deleteStream(String, ExpectedRevision), setStreamMaxAge(String, Duration), plus nested sealed
  interfaces ReadResult, AppendResult and DeleteResult. Only read and the two appends go ON the
  interface — deleteStream and setStreamMaxAge have zero callers anywhere, so they stay as ordinary
  methods on ESDBEventStore rather than forcing an in-memory implementation nothing asks for.
- read() has ZERO callers and ReadResult is referenced nowhere else, so its shape is free.
- AppendResult.Success(ExpectedRevision nextExpectedRevision, Position logPosition) IS referenced by
  core/commands/CommandBus, core/events/EventBus and their ESDB implementations. Do not change
  AppendResult, DeleteResult, or those interfaces.

Do this:
1. Extract the public surface into an interface in the same package, keeping the nested sealed
   result interfaces, with ONE change: ReadResult.Success carries `Object[] events` (already
   deserialized) instead of `ResolvedEvent[] events`.
2. Move the current class body into ESDBEventStore implements EventStore, same package, same
   constructor taking EventStoreDBClient. Its read() now deserializes each ResolvedEvent with the
   existing EventSerializer before returning ReadResult.Success.
3. Add InMemoryEventStore implements EventStore, InternalEventBus. It:
   - keeps a Map<String, List<EventEnvelope>> where EventEnvelope is a (String eventType, String
     json) record, serialized with the same Jackson configuration the workshop store uses
     (JavaTimeModule, FAIL_ON_UNKNOWN_PROPERTIES off, WRITE_DATES_AS_TIMESTAMPS off,
     ADJUST_DATES_TO_CONTEXT_TIME_ZONE off, field visibility ANY). Round-tripping through JSON
     proves every event is serializable and deep-copies stored state;
   - honours expected revisions, which the workshop store does not — this project needs it because
     every ShoppingCartCommand carries an expectedVersion and AggregateStore returns ETags:
       append with ExpectedRevision.noStream() on an existing non-empty stream
         -> AppendResult.StreamAlreadyExists(actual)
       append with an explicit revision that does not match
         -> AppendResult.Conflict(expected, actual)
       ExpectedRevision.any() always appends
       success -> AppendResult.Success(ExpectedRevision.expectedRevision(newRevision), anyPosition)
     Revisions are zero-based: a stream holding one event is at revision 0;
   - read on an unknown stream returns ReadResult.StreamDoesNotExist;
   - AFTER a successful append — and only after — dispatches each appended event to middleware and
     then to typed subscribers, synchronously and depth-first, exactly like the workshop store. A
     failed append dispatches nothing;
   - implements InternalEventBus's subscribe(Class, Consumer) and use(Consumer<Object>) for that
     dispatch.
4. Tests in src/test/java/.../core/esdb/InMemoryEventStoreTests.java covering every bullet: the
   revision cases, the JSON round trip (append a record with an OffsetDateTime and read it back
   equal), subscribers receiving appended events in order, a failed append notifying nobody, and
   depth-first dispatch. Do not test ESDBEventStore — it needs a running EventStoreDB.

Constraints: no new dependencies (Jackson is already a dependency). Do not touch core/commands,
core/events, the ESDB bus implementations, core/entities/EntityStore, or hotelmanagement.

Finish with ./gradlew build green.
```

### Step 0.3 — `AggregateStore` on the `EventStore` abstraction

```text
Continue in samples/distributed-processes. TDD.

Goal: make core/aggregates/AggregateStore work against the EventStore interface from step 0.2, so it
runs in memory, and fix its version-tracking bug.

Context:
- AggregateStore<Entity extends AbstractAggregate<Event, Id>, Event, Id> currently takes an
  EventStoreDBClient and talks to it directly.
- It is NEVER constructed anywhere in the project — only declared as a field type in
  ShoppingCartService, the two ecommerce forwarders and hotelmanagement's GuestStayAccountService.
  So its constructor and method signatures are free. No compatibility constructor is needed.
- AbstractAggregate exposes `protected Id id`, `protected int version = -1`,
  `Object[] dequeueUncommittedEvents()`, `abstract void when(Event)` and `protected void
  enqueue(Event)`. AggregateStore is in the same package, so `version` is reachable.
- Bug: get() replays by calling current.when(event) directly, bypassing the version counter, so
  version stays -1 and the no-revision getAndUpdate always passes -1. Optimistic concurrency is dead.

Do this:
1. Constructor takes (EventStore eventStore, Function<Id, String> mapToStreamId,
   Supplier<Entity> getEmpty).
2. Rewrite get() to use eventStore.read(streamId) and the ReadResult sealed interface:
   StreamDoesNotExist or NoEventsFound -> Optional.empty(); Success -> replay; UnexpectedFailure ->
   rethrow.
3. While replaying, set the entity's version to the zero-based index of the last applied event, so
   an entity rebuilt from three events has version 2.
4. Change appendEvents to take (Entity entity, ExpectedRevision expectedRevision) and call
   eventStore.append(streamId, expectedRevision, events). Translate the AppendResult: Success ->
   ETag.weak(nextExpectedRevision); StreamAlreadyExists or Conflict -> throw a clear
   IllegalStateException naming the stream and both revisions; UnexpectedFailure -> rethrow. Update
   add() and both getAndUpdate() overloads.
5. Keep the public surface otherwise identical: get, add, getAndUpdate(Consumer, Id),
   getAndUpdate(Consumer, Id, long), appendEvents.
6. Note for later steps, and worth a short comment: because the store dispatches on append,
   appending IS publishing. AggregateStore needs no event bus and facades will not publish.
7. Tests in src/test/java/.../core/aggregates/AggregateStoreTests.java using InMemoryEventStore and
   a tiny test aggregate defined in the test file: add then get round-trips state; get on an unknown
   id is empty; version after replay equals event count minus one; getAndUpdate appends and advances
   the revision; add on an existing stream throws; getAndUpdate with a stale explicit revision
   throws; and a subscriber registered on the store receives the events an add() appended.

Constraints: do not touch ecommerce or hotelmanagement — they only declare the type, so they must
keep compiling untouched.

Finish with ./gradlew build green.
```

### Step 0.4 — Test helpers

```text
Continue in samples/distributed-processes. Test infrastructure only; no production code changes.

Goal: the two helpers every later step needs.

Context: src/test/java/io/eventdriven/testing/EventSourcedSpecification.java exists but is
decider-shaped — it takes a Supplier<Entity> and a BiFunction<Entity, Event, Entity> evolve, and its
`when` is a Function<Entity, Event[]>. The ecommerce aggregates are mutable
(AbstractAggregate<Event, Id>, void when(Event), protected enqueue). It therefore cannot test them,
and hotelmanagement's GuestStayAccountCheckinTests depends on its current shape. Leave it alone and
add a sibling.

Create, in src/test/java/io/eventdriven/testing/:

1. AggregateSpecification<Entity extends AbstractAggregate<Event, Id>, Event, Id> — Given/When/Then
   for mutable aggregates:
   - given(Event... events) rebuilds an entity by calling when(...) for each event on a fresh
     instance from a Supplier<Entity> passed to the constructor, then drains uncommitted events so
     the Given phase leaves nothing behind;
   - given() with no events, for tests whose When is a static factory;
   - when(Function<Entity, Entity> handle) for factories, when(Consumer<Entity> handle) for methods
     on an existing entity;
   - then(Event... expected) asserts the uncommitted events equal the expected ones in order, with a
     failure message printing both sequences;
   - thenThrows(Class<? extends Throwable> expected) asserts the When threw that type and enqueued
     nothing.
   Keep the generics honest — EventSourcedSpecification shadows its type parameters in its inner
   builder and forces raw types at call sites. Do not repeat that.

2. MessageCatcher — a spy for the channels from steps 0.1 and 0.2. Keep the SAME surface as the
   workshops' own MessageCatcher (workshops/event-driven-architecture/solved/.../e03_businessprocesses/
   core/MessageCatcher.java and the e13/e14 copies) so a reader moving between them hits no renames:
   - a public List<Object> published, appended by catchMessage(Object), registered with
     eventBus.use(catcher::catchMessage) and commandBus.use(catcher::catchMessage);
   - reset();
   - shouldReceiveMessages(Object... expected) asserting the recorded sequence equals the expected
     one, in order, using AssertJ's usingRecursiveComparison so arrays inside records compare by
     value;
   - shouldReceiveSingleEvent(Event);
   - shouldNotReceiveAnyEvent();
   - on failure, print the full recorded transcript one message per line. These assertions are the
     documentation of the process, so a failure must be readable.

3. Prove both helpers work with one new test class,
   src/test/java/io/eventdriven/distributedprocesses/ecommerce/shoppingcarts/ShoppingCartTests.java
   — a NEW file in the ecommerce package; do not touch the existing
   io/eventdriven/distributedprocesses/shoppingcarts/ShoppingCartTests.java, which talks to a real
   EventStoreDB. Two or three cases over ShoppingCart: opening emits ShoppingCartOpened; adding a
   product item emits ProductItemAddedToShoppingCart; confirming behaves as the aggregate defines.

Constraints: AssertJ is already available through spring-boot-starter-test. No new dependencies.

Finish with ./gradlew build green.
```

---

## Phase 1 — The four modules

Four independent tracks, runnable in parallel once Phase 0 is green. They touch disjoint packages.

**Conventions every track follows** — repeat them in each prompt; they are what make the four tracks
compose without a later reconciliation step:

- A facade method takes exactly one command record and returns `void`. That is what lets
  `commandBus.handle(SomeCommand.class, facade::someMethod)` bind directly.
- A facade is constructed with its `AggregateStore` and, if it needs the clock, a
  `Supplier<OffsetDateTime> now`. **It does not publish** — the store dispatches on append.
- A forwarder is constructed with an `IntegrationEventBus`, plus its `AggregateStore` only if it
  must re-read to enrich. It publishes external events only.
- Nothing calls `OffsetDateTime.now()` or `UUID.randomUUID()`. Both arrive as suppliers.
- Each module gets a `*Config` final class with a static `configure(...)` that registers the
  module's command handlers and its own internal-event subscriptions, and returns the facade (or a
  small record when the module also owns a worker or client).
- Each aggregate gets `static String mapToStreamId(UUID id)` returning `"<Entity>-%s"`, matching
  `ShoppingCart.mapToStreamId`.

### Step A.1 — ShoppingCart facade and Config

```text
Continue in samples/distributed-processes. TDD.

Goal: turn ShoppingCartService into a facade bound to the command bus.

Context:
- ecommerce/shoppingcarts/ShoppingCartService takes an AggregateStore and a ProductPriceCalculator,
  but its methods take unpacked arguments — open(UUID, UUID), addProductItem(UUID, ProductItem,
  Long). That is why ShoppingCartCommand's records are dead code.
- ShoppingCartCommand already defines OpenShoppingCart, AddProductItemToShoppingCart,
  RemoveProductItemFromShoppingCart, ConfirmShoppingCart and CancelShoppingCart, each carrying an
  expectedVersion where relevant.
- ShoppingCart already has mapToStreamId. Its business methods are package-private.
- The InMemoryEventStore from step 0.2 dispatches events when they are appended, so the facade must
  NOT publish anything itself. Appending is publishing.

Do this:
1. Rename ShoppingCartService to ShoppingCartFacade. Each method takes its command record and
   returns void. Constructor: (AggregateStore<ShoppingCart, ShoppingCartEvent, UUID> store,
   ProductPriceCalculator productPriceCalculator, Supplier<OffsetDateTime> now).
2. Confirm and cancel need a timestamp; take it from the injected clock, not from the command.
3. Add ShoppingCartsConfig with
     public static ShoppingCartFacade configure(CommandBus commandBus, EventStore eventStore,
       ProductPriceCalculator prices, Supplier<OffsetDateTime> now)
   registering all five commands with commandBus.handle(...) and returning the facade.
4. Tests in src/test/java/.../ecommerce/shoppingcarts/ShoppingCartFacadeTests.java: wire an
   InMemoryEventStore and an InMemoryCommandBus through ShoppingCartsConfig, subscribe a recorder to
   the store, then for each command send it on the command bus and assert both the events the store
   dispatched and the state read back. Include confirming a cart with two product items — the path
   the process depends on — and a command sent with a stale expectedVersion, which must fail rather
   than silently append.

Constraints: do not change ShoppingCart, ShoppingCartEvent or ShoppingCartCommand. Do not touch
other ecommerce modules. No Spring annotations.

Finish with ./gradlew build green.
```

### Step A.2 — ShoppingCart forwarder wired

```text
Continue in samples/distributed-processes. TDD.

Goal: make ShoppingCartExternalEventForwarder reachable, so confirming a cart produces a
ShoppingCartFinalized on the integration bus.

Context:
- ecommerce/shoppingcarts/external/ShoppingCartExternalEventForwarder already exists and already
  does the right thing: on ShoppingCartConfirmed it re-reads the cart from the AggregateStore and
  publishes ShoppingCartFinalized(cartId, clientId, productItems, totalPrice, finalizedAt). It takes
  the OLD core/events/EventBus and has no caller.
- ShoppingCartFinalized exists and carries shoppingcarts.productitems.PricedProductItem. Leave its
  shape alone; the saga maps it later.
- This is the ONLY forwarder that genuinely enriches by re-reading — ShoppingCartConfirmed carries
  just the cart id and a timestamp. The others map.

Do this:
1. Change the forwarder's constructor to take core/messaging IntegrationEventBus instead of
   core/events/EventBus. Keep the enrichment logic as it is.
2. Extend ShoppingCartsConfig.configure to take an InternalEventBus and an IntegrationEventBus,
   build the forwarder, and register
   internalEvents.subscribe(ShoppingCartEvent.ShoppingCartConfirmed.class, forwarder::on).
   In the composition root the InternalEventBus will be the InMemoryEventStore itself.
3. Tests: confirming a cart with two product items publishes exactly one ShoppingCartFinalized on
   the integration bus, carrying the client id, both product items and the correct total price — and
   no other cart command puts anything on the integration bus.

Constraints: unchanged domain types. Do not touch other modules.

Finish with ./gradlew build green.
```

### Step B.1 — Order aggregate defects and unit tests

```text
Continue in samples/distributed-processes. TDD — write the failing test for each defect first.

Goal: make the Order aggregate correct enough to carry the process, and cover it with tests.

Context — real defects in ecommerce/orders:
a) Order.cancel guards with `if (status == Status.Opened || status == Status.Cancelled) throw`, so a
   freshly opened order can NEVER be cancelled. Every compensation path ends in CancelOrder on an
   order that is Opened or Paid, so this kills compensation. Throw only for Completed and Cancelled.
b) Order.when(OrderInitialized) assigns id, clientId, productItems and status but never totalPrice.
   The field stays 0, so OrderPaymentRecorded.amount is always 0.
c) OrderCommand's record components are PascalCase (UUID OrderId, UUID ClientId,
   PricedProductItem[] ProductItems, double TotalPrice, OrderCancellationReason
   CancellationReason), and OrderEvent.OrderCancelled has the same problem (UUID OrderId,
   OrderCancellationReason Reason).
d) OrderCommand.InitializeOrder imports shoppingcarts.productitems.PricedProductItem while OrderEvent
   uses orders.products.PricedProductItem. Orders' contract must use its own type.
Also: Order has no mapToStreamId, and OrderCancellationReason has only ProductWasOutOfStock.

Do this:
1. Fix (a) and (b).
2. Rename every PascalCase record component to camelCase in OrderCommand and OrderEvent, updating
   references in OrderSaga just enough to keep the project compiling. Do not otherwise change
   OrderSaga — it is rewritten in step 2.1.
3. Change InitializeOrder to carry orders.products.PricedProductItem[] and to record where the order
   came from: InitializeOrder(UUID orderId, UUID cartId, UUID clientId,
   PricedProductItem[] productItems, double totalPrice).
4. Add `public static String mapToStreamId(UUID orderId)` returning "Order-%s".
5. Extend OrderCancellationReason with PaymentFailed and Requested.
6. Tests in src/test/java/.../ecommerce/orders/OrderTests.java using AggregateSpecification:
   initialize emits OrderInitialized with the right totalPrice; recordPayment on an opened order
   emits OrderPaymentRecorded carrying the product items AND a non-zero amount (the regression test
   for defect b); complete on a paid order emits OrderCompleted; complete on an opened order throws;
   cancel on an opened order emits OrderCancelled with a null paymentId (the regression test for
   defect a); cancel on a paid order emits OrderCancelled carrying the paymentId; cancel on a
   completed order throws.

Constraints: stay inside ecommerce/orders. No facade yet.

Finish with ./gradlew build green.
```

### Step B.2 — Order facade and Config

```text
Continue in samples/distributed-processes. TDD.

Goal: give the orders module a working command side. OrderService is currently an empty class body
(`public class OrderService {}`).

Context: facades take one command record, are constructed with (AggregateStore,
Supplier<OffsetDateTime> now), and do NOT publish — the store dispatches on append.
Order.initialize/recordPayment/complete/cancel all take the timestamp as a parameter.

Do this:
1. Replace OrderService with OrderFacade handling InitializeOrder, RecordOrderPayment, CompleteOrder
   and CancelOrder. RecordOrderPayment carries its own paymentRecordedAt — use it, since that is the
   payment's timestamp, not the order's; take the clock for the others.
2. Add OrdersConfig with a static configure(CommandBus, EventStore, Supplier<OffsetDateTime>)
   registering the four commands and returning the facade.
3. Tests in src/test/java/.../ecommerce/orders/OrderFacadeTests.java wiring through OrdersConfig with
   an InMemoryEventStore and a recorder subscribed to it: each command produces the right event and
   the right stored state. Include the local sequence InitializeOrder -> RecordOrderPayment ->
   CompleteOrder, and separately InitializeOrder -> CancelOrder.

Constraints: stay inside ecommerce/orders. Do not touch OrderSaga.

Finish with ./gradlew build green.
```

### Step B.3 — Order external events and forwarder

```text
Continue in samples/distributed-processes. TDD.

Goal: publish the orders module's contract to the integration bus.

Context: the saga needs four things from orders — initialization (to request payment), payment
recorded (to send the package), cancellation (to refund), and completion for the transcript.
OrderEvent.OrderPaymentRecorded ALREADY carries productItems and amount, so this forwarder is a
straight mapping, not a re-read — unlike the shopping cart one.

Do this:
1. Add ecommerce/orders/external/OrderExternalEvent, a sealed interface with:
     OrderInitialized(UUID orderId, UUID clientId, PricedProductItem[] productItems,
                      double totalPrice, OffsetDateTime initializedAt)
     OrderPaymentRecorded(UUID orderId, UUID paymentId, PricedProductItem[] productItems,
                          double amount, OffsetDateTime recordedAt)
     OrderCompleted(UUID orderId, OffsetDateTime completedAt)
     OrderCancelled(UUID orderId, UUID paymentId /* nullable */,
                    OrderCancellationReason reason, OffsetDateTime cancelledAt)
   using orders.products.PricedProductItem.
2. Add OrderExternalEventForwarder with an on(...) per internal OrderEvent, mapping and publishing on
   the IntegrationEventBus. No AggregateStore needed.
3. Wire the four subscriptions in OrdersConfig, which now also takes an InternalEventBus and an
   IntegrationEventBus.
4. Tests in .../ecommerce/orders/OrderExternalEventForwarderTests.java: each command results in
   exactly one matching external event with the payload preserved — including a cancelled paid order
   carrying its paymentId, and a cancelled unpaid order carrying null.

Constraints: stay inside ecommerce/orders.

Finish with ./gradlew build green.
```

### Step C.1 — Payment aggregate: stream id and unit tests

```text
Continue in samples/distributed-processes. TDD.

Goal: cover the Payment aggregate and give it a stream id. It is in better shape than the others —
request/complete/discard/timeOut and their guards are already correct.

Context:
- ecommerce/payments/Payment extends AbstractAggregate<PaymentEvent, UUID>, with a private Status
  enum {Pending, Completed, Failed} and public accessors orderId() and amount().
- PaymentEvent: PaymentRequested(paymentId, orderId, amount), PaymentCompleted(paymentId,
  completedAt), PaymentDiscarded(paymentId, discardReason, discardedAt),
  PaymentTimedOut(paymentId, timedOutAt).
- DiscardReason is {UnexpectedError, OrderCancelled}. Payment has no mapToStreamId.

Do this:
1. Add `public static String mapToStreamId(UUID paymentId)` returning "Payment-%s".
2. Tests in .../ecommerce/payments/PaymentTests.java using AggregateSpecification: request emits
   PaymentRequested; complete on a pending payment emits PaymentCompleted; discard emits
   PaymentDiscarded with the reason; timeOut emits PaymentTimedOut; and each of complete, discard and
   timeOut throws on a payment that is already completed, discarded or timed out. That last group is
   nine small cases — table-drive them if it reads better, but do not skip them: they are what makes
   "a payment settles exactly once" true, and both the timeout scenario and the saga's refund guard
   depend on it.

Constraints: stay inside ecommerce/payments. No facade yet.

Finish with ./gradlew build green.
```

### Step C.2 — Payment facade and Config

```text
Continue in samples/distributed-processes. TDD.

Goal: the payments command side. PaymentService is currently an empty class body.

Do this:
1. Add PaymentFacade handling RequestPayment, CompletePayment, DiscardPayment and TimeOutPayment,
   constructed with (AggregateStore<Payment, PaymentEvent, UUID> store,
   Supplier<OffsetDateTime> now). It does not publish — the store dispatches on append.
   - requestPayment stores a pending payment. It does NOT charge anything; the charge is step C.3.
   - completePayment, discardPayment and timeOutPayment apply the corresponding aggregate methods.
2. Add PaymentsConfig.configure(CommandBus, EventStore, Supplier<OffsetDateTime>) registering the
   four commands and returning the facade.
3. Tests in .../ecommerce/payments/PaymentFacadeTests.java: RequestPayment leaves the payment pending
   and dispatches only PaymentRequested; CompletePayment dispatches PaymentCompleted;
   DiscardPayment and TimeOutPayment dispatch theirs; completing an already-completed payment
   surfaces the aggregate's failure rather than silently doing nothing.

Constraints: stay inside ecommerce/payments. No gateway yet.

Finish with ./gradlew build green.
```

### Step C.3 — The payment gateway seam

```text
Continue in samples/distributed-processes. TDD.

Goal: settlement behind an interface, so a payment genuinely sits in Pending between request and
settlement and tests can choose what happens next. This is what makes the failure handling
meaningful — and the precedent is already in the codebase: Shipment takes a
Function<ProductItem, Boolean> isProductAvailable rather than reaching for a warehouse.

Do this:
1. Add ecommerce/payments/PaymentGateway:
     public interface PaymentGateway { void charge(UUID paymentId, double amount); }
   Write a short comment — one of the few worth having here — saying a real implementation calls an
   external provider and the answer arrives later as a webhook, which in this sample is modelled by
   the implementation sending a command back.
2. Add PaymentGatewayClient, subscribed to the payments module's internal PaymentRequested, which
   calls gateway.charge(...) inside a try/catch. On ANY exception it sends
   DiscardPayment(paymentId, DiscardReason.UnexpectedError) instead of rethrowing — a failed charge
   is a business outcome, not a crash, and a handler that throws freezes the process. This is the
   article's first defence.
3. Wire the client's subscription in PaymentsConfig, which now takes an InternalEventBus, a
   PaymentGateway and the CommandBus.
4. Add three test doubles under src/test/java/.../ecommerce/payments/, each constructed with the
   CommandBus where it needs one:
   - AutoCompletingPaymentGateway — sends CompletePayment(paymentId);
   - AutoRejectingPaymentGateway — sends DiscardPayment(paymentId, UnexpectedError);
   - ThrowingPaymentGateway — throws, so the client's catch is exercised;
   - SilentPaymentGateway — does nothing at all, leaving the payment pending. This is the one the
     timeout scenario needs, so it is not dead code.
5. Tests: with the auto-completing gateway, RequestPayment leads to PaymentCompleted without anyone
   sending CompletePayment by hand; with the auto-rejecting gateway it leads to PaymentDiscarded;
   with the throwing gateway it also leads to PaymentDiscarded(UnexpectedError) and no exception
   escapes; with the silent gateway the payment stays pending and nothing further is dispatched.

Constraints: stay inside ecommerce/payments. No scheduler, no threads — the doubles settle
synchronously, which keeps the transcripts deterministic.

Finish with ./gradlew build green.
```

### Step C.4 — Payment forwarder wired

```text
Continue in samples/distributed-processes. TDD.

Goal: make the existing PaymentExternalEventForwarder reachable.

Context: ecommerce/payments/external/PaymentExternalEventForwarder already exists with three on(...)
methods that re-read the Payment to recover orderId and amount, publishing
PaymentExternalEvent.PaymentFinalized or PaymentFailed(Reason.Discarded | Reason.TimedOut). It takes
the OLD core/events/EventBus and has no caller. PaymentExternalEvent already has the right shape.

Do this:
1. Change the forwarder's constructor to take core/messaging IntegrationEventBus. Keep the logic.
2. Extend PaymentsConfig to take an IntegrationEventBus, build the forwarder and subscribe it to
   PaymentCompleted, PaymentDiscarded and PaymentTimedOut on the internal channel.
3. Tests in .../ecommerce/payments/PaymentExternalEventForwarderTests.java: a completed payment
   yields exactly one PaymentFinalized carrying the order id and amount; a discarded payment yields
   PaymentFailed with Reason.Discarded; a timed-out payment yields PaymentFailed with Reason.TimedOut;
   RequestPayment alone yields nothing on the integration bus.

Constraints: stay inside ecommerce/payments.

Finish with ./gradlew build green.
```

### Step C.5 — Pending payments and the timeout worker

```text
Continue in samples/distributed-processes. TDD.

Goal: the article's second defence — a background worker that stops the process freezing when a
payment never settles.

Context: Payment's Status is private with no accessor and there is no query side, so the worker
cannot ask the store which payments are pending. Build a tiny read model inside the payments module,
fed by its own internal events. There is no scheduler in this project and we do not want
time-dependent tests, so the worker exposes an explicit run(now).

Do this:
1. Add ecommerce/payments/PendingPayments, subscribed to the payments internal channel:
   - on PaymentRequested, record (paymentId, requestedAt). PaymentRequested carries no timestamp, so
     take it from the injected clock;
   - on PaymentCompleted, PaymentDiscarded or PaymentTimedOut, remove the entry;
   - expose List<UUID> olderThan(OffsetDateTime threshold).
2. Add ecommerce/payments/PaymentTimeoutWorker, constructed with (PendingPayments, CommandBus,
   Duration timeout), exposing `public void run(OffsetDateTime now)` which sends
   TimeOutPayment(paymentId, now) for every payment requested before now.minus(timeout). Comment that
   production would call run on a schedule; tests call it directly.
3. Wire PendingPayments' subscriptions in PaymentsConfig and return the worker alongside the facade
   — a small record such as PaymentsModule(PaymentFacade facade, PaymentTimeoutWorker worker) keeps
   the Config signature honest.
4. Tests in .../ecommerce/payments/PaymentTimeoutWorkerTests.java, using the SilentPaymentGateway
   from C.3: a payment requested and never settled is timed out once the threshold passes; running
   before the threshold sends nothing; a payment that completed before the threshold is never timed
   out; running twice does not send TimeOutPayment twice, because the first removed the entry —
   assert this, it is what keeps the compensation path from firing repeatedly.

Constraints: stay inside ecommerce/payments. No scheduler, no sleeps, no Awaitility.

Finish with ./gradlew build green.
```

### Step D.1 — Shipment aggregate: defects and delivery

```text
Continue in samples/distributed-processes. TDD — failing test first for each change.

Goal: make the shipments module coherent, and add the delivery step the process now needs.

Context — defects in ecommerce/shipments:
a) shipments/PaymentService.java contains `public class PaymentService {}` — a copy-paste artefact.
   Delete it.
b) ShipmentCommand's components are PascalCase: DeliverPackage(UUID Id), SendPackage(UUID OrderId,
   ProductItem[] ProductItems).
c) SendPackage carries no shipmentId, so the facade has no id to store the shipment under.
d) Shipment's logic lives in a public constructor; no static factory, no mapToStreamId.
e) Shipment.when never assigns the orderId field, though both events carry it.
And one addition: the process completes an order when a package is DELIVERED, not when it is sent,
so the aggregate needs delivery behaviour.

The interesting behaviour is already right: Shipment takes Function<ProductItem, Boolean>
isProductAvailable and emits ProductWasOutOfStock rather than throwing — the article's "failure is an
event, not an exception" rule, already in place. Keep it.

Do this:
1. Delete shipments/PaymentService.java.
2. camelCase the ShipmentCommand components and add shipmentId:
   SendPackage(UUID shipmentId, UUID orderId, ProductItem[] productItems) and
   DeliverPackage(UUID shipmentId).
3. Add `public static Shipment send(Function<ProductItem, Boolean> isProductAvailable,
   UUID shipmentId, UUID orderId, ProductItem[] productItems, OffsetDateTime now)`, make the
   constructor private, add `public static String mapToStreamId(UUID shipmentId)` returning
   "Shipment-%s", and assign orderId in when(...) for every event.
4. Add ShipmentEvent.PackageWasDelivered(UUID shipmentId, UUID orderId, OffsetDateTime deliveredAt)
   and a `deliver(OffsetDateTime now)` method that emits it. Guard it: delivering a shipment that was
   never sent — one that went out of stock, or one already delivered — throws.
5. Update OrderSaga's references only as far as needed to keep the project compiling; it is rewritten
   in step 2.1.
6. Tests in .../ecommerce/shipments/ShipmentTests.java using AggregateSpecification: all items
   available emits PackageWasSent with items and timestamp; one unavailable item emits
   ProductWasOutOfStock and no PackageWasSent; delivering a sent package emits PackageWasDelivered;
   delivering an out-of-stock shipment throws; delivering twice throws. For an empty item array,
   assert whatever the code does (allMatch on an empty stream is true) — and if that reads wrong, say
   so rather than silently changing it.

Constraints: stay inside ecommerce/shipments, apart from the minimal OrderSaga fix.

Finish with ./gradlew build green.
```

### Step D.2 — Shipment facade and Config

```text
Continue in samples/distributed-processes. TDD.

Goal: the shipments command side, now covering both halves of the module.

Do this:
1. Add ShipmentFacade constructed with (AggregateStore<Shipment, ShipmentEvent, UUID> store,
   Function<ProductItem, Boolean> isProductAvailable, Supplier<OffsetDateTime> now), handling
   SendPackage and DeliverPackage. It does not publish — the store dispatches on append.
   Note a shipment is always stored: the out-of-stock case is a stored ProductWasOutOfStock event,
   not an absent stream.
2. Add ShipmentsConfig.configure(CommandBus, EventStore, Function<ProductItem, Boolean>,
   Supplier<OffsetDateTime>) registering both commands and returning the facade.
3. Tests in .../ecommerce/shipments/ShipmentFacadeTests.java: SendPackage with everything available
   dispatches PackageWasSent and stores a shipment; with one item unavailable dispatches
   ProductWasOutOfStock and still stores a shipment; DeliverPackage on a sent shipment dispatches
   PackageWasDelivered; DeliverPackage on an out-of-stock shipment surfaces the aggregate's failure.

Constraints: stay inside ecommerce/shipments.

Finish with ./gradlew build green.
```

### Step D.3 — The delivery seam

```text
Continue in samples/distributed-processes. TDD.

Goal: mirror the payment gateway on the shipping side, so a package is delivered by something
outside the module rather than by a test poking the middle of the process.

Context: step C.3 put the card charge behind PaymentGateway, called by a PaymentGatewayClient that
reacts to the module's own internal PaymentRequested. Do the same here. Keep the two seams
recognisably alike — a reader who understands one should recognise the other immediately.

Do this:
1. Add ecommerce/shipments/DeliveryProvider:
     public interface DeliveryProvider { void deliver(UUID shipmentId, ProductItem[] productItems); }
   with the same short comment about a real provider calling back later.
2. Add DeliveryProviderClient, subscribed to the module's internal PackageWasSent, calling
   deliver(...) inside a try/catch. On an exception, log and leave the shipment undelivered — there
   is no "delivery failed" event in this process, and inventing one is out of scope. Say that in a
   comment rather than silently swallowing.
3. Wire the subscription in ShipmentsConfig, which now takes an InternalEventBus, a DeliveryProvider
   and the CommandBus.
4. Test doubles under src/test/java/.../ecommerce/shipments/: AutoDeliveringDeliveryProvider (sends
   DeliverPackage) and SilentDeliveryProvider (does nothing — used by the out-of-stock and
   compensation scenarios, where nothing should be delivered).
5. Tests: with the auto-delivering provider, SendPackage leads to PackageWasDelivered without anyone
   sending DeliverPackage by hand; with the silent provider the shipment stays sent and undelivered;
   an out-of-stock shipment never reaches the provider at all.

Constraints: stay inside ecommerce/shipments. Synchronous doubles, no threads.

Finish with ./gradlew build green.
```

### Step D.4 — Shipment external events and forwarder

```text
Continue in samples/distributed-processes. TDD.

Goal: publish the shipments module's contract, so the saga stops importing ShipmentEvent — the leak
that makes the current OrderSaga depend on another module's internals.

Context: all three shipment events already carry shipmentId, orderId and a timestamp, so this
forwarder maps rather than re-reads.

Do this:
1. Add ecommerce/shipments/external/ShipmentExternalEvent, a sealed interface with
     PackageWasSent(UUID shipmentId, UUID orderId, ProductItem[] productItems, OffsetDateTime sentAt)
     PackageWasDelivered(UUID shipmentId, UUID orderId, OffsetDateTime deliveredAt)
     ProductWasOutOfStock(UUID shipmentId, UUID orderId, ProductItem[] productItems,
                          OffsetDateTime availabilityCheckedAt)
2. Add ShipmentExternalEventForwarder publishing them on the IntegrationEventBus, and wire all three
   subscriptions in ShipmentsConfig, which now also takes an IntegrationEventBus.
3. Tests in .../ecommerce/shipments/ShipmentExternalEventForwarderTests.java: each internal event
   yields exactly one matching external event with the payload preserved.

Constraints: stay inside ecommerce/shipments.

Finish with ./gradlew build green.
```

---

## Phase 2 — Wiring the process

Sequential, and only after all four Phase 1 tracks are merged and green.

### Step 2.0 — One command path, and derived strongly typed identifiers  *(done)*

Delivered, and different from the first draft of this step. Recorded here so the next reader is not
misled:

1. `AggregateStore` has **one path**, `getAndUpdate(id, [expectedVersion,] Consumer<Entity>)`. `add`,
   `addIfAbsent` and the old `(Consumer, Id)` argument order are gone. Version `-1` maps to
   `ExpectedRevision.noStream()`, so create and update are the same code.
2. **No event means no change.** A handler that enqueues nothing causes no append and no revision
   move. Idempotency therefore lives in the aggregate, not in a store method.
3. Every create became an instance method with a guard: `ShoppingCart.open`, `Order.initialize`,
   `Payment.request`, `Shipment.send`, and `hotelmanagement`'s `GuestStayAccount.open`.
4. `core/identifiers/Urns` is a static format helper. `EntityId` supplies `tail()`.
5. Four typed ids — `ShoppingCartId`, `OrderId`, `PaymentId`, `ShipmentId` — each a record over a URN
   string, validating its own segment in a compact constructor that is also the `@JsonCreator`.
6. `Payment` and `Shipment` hold an opaque `String referenceId`, never an `orderId`. See spec §5.3.1.
7. Jackson bumped to 2.18.2. `OrderIdSerializationTests` pins the `@JsonValue` behaviour and must
   stay — it already caught `mapToStreamId` leaking a whole URN into the stream name.
8. `AggregateSpecification` lost `FactoryWhen`; one `given(events…)` now covers every case.

### Step 2.1 — Rewrite OrderSaga

```text
Continue in samples/distributed-processes. TDD.

Goal: rewrite ecommerce/orders/OrderSaga so it coordinates the process using only other modules'
PUBLISHED contracts, and cover every step with unit tests.

Context:
- The saga keeps its injected command bus and its on(event) methods — a deliberate decision, not an
  oversight. It holds no state.
- Today it imports payments.PaymentCommand, payments.external.PaymentExternalEvent,
  shipments.ProductItem, shipments.ShipmentCommand, shipments.ShipmentEvent and
  shoppingcarts.external.ShoppingCartFinalized. ShipmentEvent and its own OrderEvent are INTERNAL
  types — that is the leak this step closes.
- After Phase 1 the external contracts exist: shoppingcarts.external.ShoppingCartFinalized,
  orders.external.OrderExternalEvent, payments.external.PaymentExternalEvent,
  shipments.external.ShipmentExternalEvent.
- Today the saga uses UUID.randomUUID() inline and passes event.cartId() as the order id. Both
  change. Step 2.0 added core/identifiers/DeterministicUuid; the saga DERIVES each new id from the
  id that caused it, so a redelivered message produces the same id and AggregateStore.add ignores
  the duplicate. The saga takes no Supplier<UUID> — it is a pure function of the incoming event.

Do this:
1. Constructor: (CommandBus commandBus), using core/messaging CommandBus. No id supplier, no clock.
1a. Derive the order id in the saga, and translate the opaque reference back:
      OrderId.derivedFrom(event.cartId().value())
      new OrderId(event.referenceId())      // reading a payment's or shipment's reference back
    RequestPayment and SendPackage carry ONLY the reference, like a gateway call — payments and
    shipments derive their own ids. The saga must not name PaymentId or ShipmentId.
    Payments and shipments carry a String referenceId, not an orderId. The saga is the ONLY place
    that knows the reference holds an order urn, and `new OrderId(...)` validates that on the way in.
2. Import ONLY external events and other modules' command records. If you find yourself importing an
   internal event type, the contract is missing — add it rather than reaching through.
3. Happy path. The saga asks for BOTH outcomes at once and records each one as it lands. It never
   decides that the order is finished — the order decides that itself.
     on(ShoppingCartFinalized)                      -> InitializeOrder(OrderId.derivedFrom(cartId),
                                                        cartId, clientId, productItems, totalPrice)
     on(OrderExternalEvent.OrderInitialized)        -> RequestPayment(orderId.value(), totalPrice)
                                                    AND SendPackage(orderId.value(), productItems)
     on(PaymentExternalEvent.PaymentFinalized)      -> RecordOrderPayment(new OrderId(referenceId),
                                                        paymentId, finalizedAt)
     on(ShipmentExternalEvent.PackageWasDelivered)  -> RecordOrderShipment(new OrderId(referenceId),
                                                        shipmentId, deliveredAt)
   Note the last one: the shipment counts as done on DELIVERY, not dispatch. PackageWasSent is still
   published and still appears in the transcript; the saga simply does not act on it. Do not add an
   on(PackageWasSent) handler.
4. Compensation. Both failures are RECORDED against the order, which then cancels itself:
     on(ShipmentExternalEvent.ProductWasOutOfStock) -> RecordOrderShipmentFailure(orderId, checkedAt)
     on(PaymentExternalEvent.PaymentFailed)         -> RecordOrderPaymentFailure(orderId, failedAt)
     on(OrderExternalEvent.OrderCancelled)          -> DiscardPayment(paymentId, OrderCancelled),
       but ONLY when paymentId is not null.
   One guard, not two: a failed payment never reaches the order as a paymentId, so a cancellation
   caused by a failed payment already carries null and refunds nothing.
5. Three product-item types meet here and the mapping belongs in the saga, not in the modules:
   shoppingcarts.productitems.PricedProductItem (nested ProductItem plus unitPrice) ->
   orders.products.PricedProductItem (flat productId, quantity, unitPrice) ->
   shipments.ProductItem (productId, quantity). Two small private static mappers, tested through the
   saga's behaviour.
6. Tests in .../ecommerce/orders/OrderSagaTests.java: the saga over an InMemoryCommandBus with a
   recording handler per command type. One @Test per on(...) method asserting the exact command sent,
   and one asserting nothing is sent — on(OrderCancelled) with a null paymentId. Assert the mapped
   product items, not just the ids.
   Add one @Test proving the derivation is stable: handling the SAME event twice sends two IDENTICAL
   commands. That is the test that would have caught UUID.randomUUID().

Constraints: the saga stays stateless — the command bus is its only field. No persistence, no store,
no clock, no id supplier. Do not wire it up; that is step 2.2.

Finish with ./gradlew build green.
```

### Step 2.0g — Name what a payment reversal actually is  *(small, agreed)*

`DiscardPayment` names no gateway operation, and `DiscardReason` hides two unrelated ones. See
spec §3.3 for the research. This step renames only. It adds no authorisation, no capture and no
reservation — those are step 2.0h.

```text
Work in samples/distributed-processes. Rename only; do not add an authorise or capture step.

Split PaymentCommand.DiscardPayment into two commands, each named for the operation it performs
and each guarded on the state it applies to:

  DeclinePayment(PaymentId paymentId, DeclineReason reason)   // the charge never succeeded
  RefundPayment(PaymentId paymentId)                          // the charge succeeded, give it back

1. PaymentEvent: PaymentDiscarded -> PaymentDeclined(paymentId, DeclineReason reason, declinedAt),
   and add PaymentRefunded(paymentId, refundedAt).
2. DiscardReason -> DeclineReason, keeping ONLY UnexpectedError. OrderCancelled leaves the enum:
   it was never a reason a charge failed, it is why we give the money back, and RefundPayment
   carries no reason.
3. Payment.discard(...) -> two methods:
     decline(DeclineReason, now) — acts only while Pending, otherwise returns
     refund(now)                 — acts only when Completed, otherwise returns
   THIS IS THE DEFECT FIX. discard() guarded on Pending, but the saga's refund always arrives at a
   Completed payment, so the refund never happened. It threw until 2.0e, and has failed silently
   since. Add Status.Refunded.
4. PaymentFacade, PaymentsConfig, PaymentGatewayClient (which sends DeclinePayment(UnexpectedError)
   on a thrown charge) and the auto-rejecting gateway double follow the rename.
5. PaymentExternalEvent.PaymentFailed.Reason: Discarded -> Declined. The forwarder maps
   PaymentDeclined and PaymentTimedOut onto it. PaymentRefunded needs no external event — nothing
   consumes one.
6. OrderSaga: on(OrderCancelled) sends RefundPayment(paymentId) when paymentId is not null.
7. Tests: rename the existing cases, and ADD one that would have caught the defect — refunding a
   COMPLETED payment emits PaymentRefunded. Assert through PaymentFacade, not only the aggregate.

Shipments are untouched in this step. SendPackage really does send; there is no reserve step to
rename yet.
```

### Step 2.0h — Two reversible holds  *(full, approved)*

Authorise and capture on the card, reserve and release in the warehouse, a deadline on both, and an
order that hears every deadline instead of hanging. Spec §3 records the shape and §3.2 the deadlines.

```text
Work in samples/distributed-processes. TDD, one module at a time, ./gradlew build green between them.

The process becomes two phases. The order takes two reversible HOLDS, joins them, and only then
commits: the parcel leaves, and the funds are captured as it goes.

1. PAYMENTS — the card lifecycle, named as a gateway names it.
   Commands:  AuthorizePayment(referenceId, amount)      -> PaymentAuthorizationRequested (Pending)
              ConfirmPaymentAuthorization(paymentId)     -> PaymentAuthorized(..., expiresAt)
              CapturePayment(paymentId)                  -> PaymentCaptured      (guard Authorized)
              VoidPayment(paymentId)                     -> PaymentVoided        (guard Authorized)
              RefundPayment(paymentId)                   -> PaymentRefunded      (guard Captured)
              DeclinePayment(paymentId, reason)          -> PaymentDeclined      (guard Pending)
              TimeOutPayment(paymentId, at)              -> PaymentTimedOut      (guard Pending)
              ExpirePaymentAuthorization(paymentId, at)  -> PaymentAuthorizationExpired
                                                                                 (guard Authorized)
   Void is the pre-capture reversal. Stripe and Adyen call it cancel, the card networks call it an
   authorisation reversal; all three mean the hold drops and no money ever moved.
   PaymentGateway gains authorize/capture/voidAuthorization/refund. Only authorize answers back.
   External: PaymentAuthorized, PaymentCaptured, PaymentFailed(Declined|TimedOut|
   AuthorizationExpired). A void and a refund publish nothing.
   AuthorizedPayments + AuthorizationExpiryWorker mirror PendingPayments + PaymentTimeoutWorker,
   keyed on the expiresAt the event already carries.

2. SHIPMENTS — reserve, then send.
   Commands:  ReserveStock(referenceId, productItems) -> StockReserved(..., reservedUntil)
                                                         | ProductWasOutOfStock
              SendPackage(shipmentId)                 -> PackageWasSent        (guard Reserved)
              DeliverPackage(shipmentId)              -> PackageWasDelivered   (guard Sent)
              ReleaseStock(shipmentId)                -> StockReleased         (guard Reserved)
              ExpireStockReservation(shipmentId, at)  -> StockReservationExpired (guard Reserved)
   SendPackage now takes only the id: the reservation already created the shipment and holds the
   items. StockReservations + ReservationExpiryWorker mirror the payment pair.
   External: StockReserved, ProductWasOutOfStock, PackageWasSent, PackageWasDelivered,
   StockReservationExpired. A release publishes nothing.

3. ORDERS — two joins, not one.
   payment:  Pending -> Authorized -> Captured, or Failed
   shipment: Pending -> Reserved -> Sent -> Delivered, or Failed
   Both holds in                 -> OrderConfirmed(orderId, shipmentId, confirmedAt)
   Captured and Delivered        -> OrderCompleted
   One Failed, other not Pending -> OrderCancelled(..., paymentState, shipmentState, ...)
   The order republishes what the saga cannot know: OrderPackageSent carries the paymentId, which is
   what makes CapturePayment possible. OrderCancelled carries where each participant stood, so the
   ORDER decides between a void, a refund and a release, and the saga only translates.
   Keep every method idempotent: a late or repeated record appends nothing and returns.

4. SAGA — thirteen handlers, in process order, hold phase then commit phase then compensation.
   The two expiry events are the reason the order can never hang; both land on
   RecordOrderShipmentFailure / RecordOrderPaymentFailure and the order decides from there.

5. TESTS — new cases for: a reservation that expires cancels the order and voids the authorisation;
   an authorisation that expires cancels the order and releases the stock; a payment declined while
   stock is reserved releases that stock and ships nothing; both arrival orders of the two holds
   confirm the same order; a second, late hold record changes nothing.

Do not wire ECommerceConfig; that is step 2.2. Finish with ./gradlew build green.
```

### Step 2.2 — ECommerceConfig and the happy-path transcript

```text
Continue in samples/distributed-processes. TDD — write the transcript test first and watch it fail.

Goal: the composition root, and the end-to-end test that proves the process runs. This is the step
the whole exercise is for: after it, reading eight subscribe lines is reading the whole process.

Context: all four module Configs exist and register their own commands, forwarders and settlement
clients. The saga exists and is unwired. Nothing yet creates the store, the buses, or connects the
modules.

Do this:
1. Add ecommerce/ECommerceConfig with a static configure(...) that:
   - takes a Supplier<OffsetDateTime> now, a Supplier<UUID> newId, a PaymentGateway, a
     DeliveryProvider, a ProductPriceCalculator and a Function<ProductItem, Boolean>
     isProductAvailable — every decision a test might steer, and nothing else;
   - creates an InMemoryEventStore, an InMemoryCommandBus and an InMemoryEventBus for integration
     events. The store IS the internal channel: it implements InternalEventBus and dispatches on
     append, so a module's internal events never touch the integration bus and can never reach the
     saga;
   - calls the four module Configs;
   - creates the OrderSaga and subscribes its eight handlers on the INTEGRATION bus, in process
     order, happy path first and compensation after, with a blank line between them. This list is
     the documentation of the process — treat its readability as a requirement. Add a one-line
     comment on the compensation block noting that manual compensation enters through the same
     command bus, so a reader looking for the operator hook finds it;
   - returns a record holding the four facades, the payment timeout worker, the store and the two
     buses, so tests can drive and observe the process.
2. Add src/test/java/.../ecommerce/OrderProcessTests.java with: a fixed clock, a deterministic id
   supplier, an AutoCompletingPaymentGateway, an AutoDeliveringDeliveryProvider, an all-available
   stock function, and a MessageCatcher registered as middleware on BOTH the integration event bus
   and the command bus — not on the store, whose internal events are module noise.
3. One test: the happy path. Open a cart, add two product items, confirm it — and then nothing else.
   The gateway settles the payment and the provider delivers the package on their own, so the whole
   process runs from that single confirm. Assert the FULL interleaved transcript with
   shouldReceiveMessages, in order, roughly:
     ShoppingCartFinalized, InitializeOrder, OrderInitialized(external), RequestPayment,
     CompletePayment, PaymentFinalized, RecordOrderPayment, OrderPaymentRecorded(external),
     SendPackage, PackageWasSent(external), DeliverPackage, PackageWasDelivered(external),
     CompleteOrder, OrderCompleted(external)
   Adjust the list to what the wiring actually produces, but assert the whole sequence, not a subset
   — a transcript that only checks the last message proves nothing about the middle.
4. If the order comes out surprising, do not reorder assertions to match. Work out why first: the
   in-memory channels dispatch depth-first, so one confirm cascades to completion before control
   returns.

Constraints: no Spring, no HTTP, no EventStoreDB, no Awaitility, no sleeps. The whole test runs
synchronously in memory.

Finish with ./gradlew build green.
```

---

## Phase 3 — Failure paths

Three tracks, parallel after 2.2. All three add scenarios to `OrderProcessTests`, so merge them one
at a time.

### Step 3.1 — Out of stock, and a rejected payment

```text
Continue in samples/distributed-processes. TDD.

Goal: two compensation scenarios, end to end. Both defences are already built — this step proves they
work through the whole process.

Do this, adding to src/test/java/.../ecommerce/OrderProcessTests.java:
1. Out of stock: configure with a stock function reporting one product unavailable, an
   AutoCompletingPaymentGateway and a SilentDeliveryProvider. Confirm a cart and assert the
   transcript continues past the payment with:
     ProductWasOutOfStock(external), CancelOrder, OrderCancelled(external), DiscardPayment,
     PaymentFailed(external, Discarded)
   Then read the order and the payment back from the store and assert they are Cancelled and
   discarded. The transcript says what was said; the store says what is true; a compensation test
   should check both.
2. Rejected payment: configure with an AutoRejectingPaymentGateway. Confirm a cart and assert:
     PaymentFailed(external, Discarded), CancelOrder(PaymentFailed), OrderCancelled(external)
   and that NO DiscardPayment follows — the payment has already failed and has nothing to refund.
   This is the saga guard added in step 2.1; prove it end to end, not just in the unit test.
3. Repeat (2) with the ThrowingPaymentGateway. The transcript should be identical, because the
   client's catch turns the exception into the same DiscardPayment. If it is not identical, that is a
   finding worth reporting rather than papering over.

Constraints: no production changes unless a test exposes a genuine defect; if it does, fix it with
its own regression test and say so.

Finish with ./gradlew build green.
```

### Step 3.2 — Payment timeout

```text
Continue in samples/distributed-processes. TDD.

Goal: prove the article's second defence end to end — a payment that never settles does not freeze
the process.

Context: PaymentTimeoutWorker.run(now) and PendingPayments exist from step C.5, ECommerceConfig
returns the worker, and SilentPaymentGateway from C.3 is the gateway that never calls back.

Do this, adding to src/test/java/.../ecommerce/OrderProcessTests.java:
1. Configure with the SilentPaymentGateway. Confirm a cart so a payment is requested, and then send
   nothing — no CompletePayment, no callback.
2. Call worker.run(now.plus(timeout).plusSeconds(1)) and assert the transcript continues:
     TimeOutPayment, PaymentFailed(external, TimedOut), CancelOrder(PaymentFailed),
     OrderCancelled(external)
   and that no DiscardPayment follows, for the same reason as in 3.1.
3. Assert that calling run again sends no further commands — the payment is no longer pending, so the
   compensation fires exactly once. A process that compensates twice is worse than one that never
   compensates.
4. Assert that calling run BEFORE the threshold sends nothing at all.

Constraints: no sleeps, no Awaitility, no scheduler. Time is the injected Supplier<OffsetDateTime>
and the explicit argument to run.

Finish with ./gradlew build green.
```

### Step 3.3 — Manual compensation, and the unpaid order

```text
Continue in samples/distributed-processes. TDD.

Goal: the article's third defence — the operator's ace up the sleeve — plus the edge case that guards
the refund.

Context: manual compensation needs no special code path. An operator sends
CancelOrder(orderId, OrderCancellationReason.Requested) on the command bus, and the resulting
external OrderCancelled carries the paymentId when one exists, so the refund goes through the same
DiscardPayment step as every other compensation.

Do this, adding to src/test/java/.../ecommerce/OrderProcessTests.java:
1. Manual compensation of a paid order: configure with an AutoCompletingPaymentGateway and a
   SilentDeliveryProvider, so the process stalls after the package is sent. Then send
   CancelOrder(orderId, Requested) on the command bus and assert the transcript continues with
   OrderCancelled(external) carrying the paymentId, then DiscardPayment, then
   PaymentFailed(external, Discarded). Read the order and payment back and assert their final state.
2. An order cancelled before it has a payment: configure with the SilentPaymentGateway, confirm a
   cart, then send CancelOrder(orderId, Requested) while the payment is still pending. Assert
   OrderCancelled(external) carries a null paymentId and that NO DiscardPayment is sent — the saga's
   null guard, proven through the whole process.
3. Both scenarios use the id supplier to know the order id without guessing. If that reads awkwardly,
   capture the id from the transcript instead; do not hard-code a UUID.

Constraints: no new commands, no operator API, no HTTP.

Finish with ./gradlew build green.
```

---

## Phase 4 — Documentation

### Step 4.1 — README

```text
Continue in samples/distributed-processes. No production code changes.

Goal: rewrite samples/distributed-processes/README.md so someone landing on the sample understands
the process without reading every file.

Context: the current README describes an intended design that drifted from the code — it calls the
command bus method `send` where the old code says `schedule`, for instance. Rewrite it against what
now exists.

Cover, and keep it tight — no file-by-file walkthrough, no implementation minutiae:
1. The process in prose: cart -> order -> payment -> shipment, which module owns each step, and that
   an order completes on delivery rather than dispatch.
2. A mermaid sequence diagram of the happy path, and a second for the compensation branches.
3. Why each module has internal events and a published external contract, and what the forwarders do
   — including the honest detail that only the shopping cart forwarder enriches by re-reading; the
   others map.
4. That the in-memory event store publishes on append, following the introduction-to-event-sourcing
   workshop, so there is no store-then-publish gap and facades never publish.
5. Where the saga is wired (ECommerceConfig) and why the subscription list is written out by hand.
6. Why the saga is stateless, and the trick that keeps it so: PaymentFinalized does not carry the
   product items, so the saga routes through RecordOrderPayment and picks them up from the order's
   own event.
7. The two settlement seams, PaymentGateway and DeliveryProvider, and why neither a charge nor a
   delivery happens inside a command handler.
8. The three defences when things go wrong: failure events instead of exceptions, the timeout worker,
   manual compensation.
9. A short "known follow-ups" section: core/messaging duplicates the older core/commands and
   core/events, which stay because hotelmanagement depends on them; and the process runs on in-memory
   infrastructure, with the ESDB implementations left unwired.

Link the article "What can go wrong with distributed systems? Everything!". Do NOT link the .NET
ECommerce sample.

Finish with ./gradlew build green.
```

---

## 2. Notes for whoever runs this

- **The transcript tests are the deliverable.** If a step's test asserts a subset of the sequence,
  the step is not done. A process test that checks only the final message would pass on a process
  that took a completely different route.
- **Never assume a failure predates your change.** If `./gradlew build` goes red, find out why
  before moving on; every step here starts from green.
- **Appending is publishing.** The store dispatches on append, so no facade should ever call a bus.
  If you catch yourself writing `store...; bus.publish(...)`, the store is not being used properly.
- **The saga only sees the integration bus.** The easiest way to break the design quietly is to
  subscribe it to a module's internal events. If a saga import points at an internal type, the
  contract is missing.
- **No `OffsetDateTime.now()` and no `UUID.randomUUID()` outside the composition root.** Both are
  injected, which is what makes the transcripts assertable.
- **`OrderProcessTests` grows across Phase 3.** Keep each scenario in its own `@Test` with a name
  that reads as a sentence, and keep the Given/When/Then comments the workshop tests use.
