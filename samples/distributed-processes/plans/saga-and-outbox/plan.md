# E-commerce: one saga, event store as outbox, realistic settlement

Scope: `samples/distributed-processes`, package `ecommerce`. State: [todo.md](todo.md).

## What we agreed

1. One stateless `OrderSaga` with only a bus, `on(event)` grouped as Hold, Commit and Compensation. It consumes the Order's internal events and the public events of the other modules.
2. The event store is logically the outbox. `append` only writes. Subscribers read one global log, each from its own checkpoint. Delivery runs after the outermost append and is never nested. A failing subscriber keeps its checkpoint and gets the event again. Projections, forwarders, the saga and the provider clients all subscribe to the store.
3. Two abstractions: `EventStore` (append, read, subscribe) and one `MessageBus` for commands and public events.
4. Each forwarder updates its own summary, then publishes.
5. Capture is a request/answer seam, like authorization. A failed capture becomes `PaymentFailed(CaptureFailed)`. The order has already shipped, so it stays open for a person.
6. Void stays fire-and-forget, because an uncaptured hold lapses by itself. Refund goes, because nothing can trigger it. On payment timeout, the provider request is cancelled.
7. Timeouts are to-do lists with a `@Scheduled handleAll()`, as in the workshop. Tests call `handleAll()`. There is no order-level timeout.
8. An expired reservation cancels with "could not ship", not "out of stock". `PaymentAuthorizationRequested` carries `requestedAt`.
9. End-to-end tests assert full transcripts with one `MessageCatcher`.

## Open questions

- Q1 (step 3): `ESDBEventStore` must implement `subscribe`. Recommended: a minimal implementation on `ESDBSubscription` over `$all`, without tests (they need Docker). Alternative: delete it, because nothing creates it.
- Q2 (step 2): the saga goes in `orders/OrderSaga.java`, as on main.

## Rules for every step

TDD. After each step, `./gradlew test` is green and the output is pristine (the SLF4J and `When.MAYBE` warnings existed before; report them only). Tests assert behaviour, not log lines. EventStoreDB for `shoppingcarts/ShoppingCartTests` runs with `docker compose up -d eventstore.db`. Aggregates stay idempotent and never throw. Comments only for justified complexity; remove a comment only if it became false, and name it. Update the README for what the step changes. No git. All steps are sequential.

## Steps

### 1. Green build, full transcripts

```text
Fix the test compilation: ECommerceTests uses deliverBookedParcels() and
CallingBackDeliveryProvider.booking, and ShipmentExternalEventForwarderTests uses
SilentDeliveryProvider.booking. Use confirmBookings(), deliverParcels(), and the carrier and
trackingNumber constants.

Then rewrite ECommerceTests in the style of the workshop's BusinessProcessTests: Given/When/Then,
one MessageCatcher on the command bus and on the integration bus, and the full ordered transcript as
records (MessageCatcher.shouldReceiveMessages). Derive the expected values from the test data: derived
ids, the fixed now, the configured durations, the carrier constants. Derive the transcript from the
flow, not from the actual output. If they differ, stop and report.
Keep the four scenarios. Drop the class-sequence assertions. Keep a gateway assertion only for void.
```

### 2. One OrderSaga

```text
Merge OrderPlacingPolicy, OrderFulfillingPolicy and OrderCancellingPolicy back into
orders/OrderSaga.java, in the shape of the original on main (git show f7166346:samples/
distributed-processes/src/main/java/io/eventdriven/distributedprocesses/ecommerce/orders/OrderSaga.java),
with today's behaviour. Merge their tests into OrderSagaTests first. Wire it in
OrdersConfig.configureProcess.
Delete the policies and the placing/, fulfilling/ and cancelling/ packages. Move PendingOrders to
orders/pending. Behaviour and transcripts must not change.
```

### 3. Messaging: event store as outbox, one MessageBus

```text
Answer Q1 first.

InMemoryEventStore:
- a global log with positions; AppendResult.Success returns the real position; EventMetadata gets
  logPosition;
- each subscription keeps its own checkpoint;
- append writes, then dispatches, unless a dispatch is already running;
- dispatch walks the log entry by entry and delivers each entry to every subscription that is
  behind;
- a handler that throws keeps its checkpoint and is skipped for the rest of the dispatch; the error
  is logged and append does not throw;
- the next dispatch delivers the entry again.
Test first, in InMemoryEventStoreTests: an append from inside a handler is not nested; a failing
subscriber does not fail the append; the failed entry is redelivered before newer ones.

Move subscribe and subscribeWithMetadata into EventStore. Apply Q1 to ESDBEventStore.
Add MessageBus (publish, subscribe, use) and InMemoryMessageBus, with tests, in the shape of the
workshop's core/MessageBus. Use it for commands and public events everywhere in ecommerce.
Configs take (eventStore, messageBus, ...).
Delete CommandBus, EventBus, IntegrationEventBus, InternalEventBus, InMemoryCommandBus,
InMemoryEventBus and their tests. A command without a handler no longer throws; this is accepted.

Each forwarder updates its own summary before it publishes; subscribe only the forwarder for the
types it forwards.
Update the transcripts for the new order. Any missing or extra message is a bug: stop and report.
Remove the ordering comments in the configs that became false, and name each one.
```

### 4. Payment settlement

```text
Capture seam:
- Payment: requestCapture (Authorized → PaymentCaptureRequested), confirmCapture
  (→ PaymentCaptured), failCapture (→ PaymentCaptureFailed); all idempotent.
- Commands: CapturePayment (now a request), ConfirmPaymentCapture, FailPaymentCapture.
- CaptureGatewayClient handles PaymentCaptureRequested: it calls the gateway and answers with
  confirm, or with FailPaymentCapture(UnexpectedError) on an exception.
- The forwarder publishes PaymentFailed(CaptureFailed). Update PaymentSummaries and
  AuthorizedPayments.
- OrderSummaries and PendingOrders handle OrderShipmentDelivered, because delivery can now come
  before the capture.

Also:
- Remove refund: refund(), RefundPayment, PaymentRefunded, the handler, the summary status,
  PaymentGateway.refund, and the tests.
- On PaymentTimedOut, AuthorizationGatewayClient calls voidAuthorization; a failure is logged, not
  rethrown.
- Fix the PaymentGateway class comment: capture answers back; void and cancel do not.
- Add requestedAt to PaymentAuthorizationRequested; PendingPayments uses it and no longer takes a
  clock.

New end-to-end scenario: a failed capture after shipping leaves the order open, with no
cancellation, void or release; after delivery it is still not completed; PendingOrders still lists
it. Update the gateway test doubles.
```

### 5. Timeouts

```text
Turn PaymentTimeoutWorker, AuthorizationExpiryWorker and ReservationExpiryWorker into to-do lists:
handle(now) does what run(now) did, and a Scheduled variant has
@Scheduled(fixedRate = 1, timeUnit = MINUTES) handleAll() calling handle(now.get()), not wired
into Spring. The modules expose the Scheduled variant. Remove PendingOrders.olderThan.

Pass the reason from StockReservationFailed into RecordOrderShipmentFailure. An expired
reservation cancels with OrderCancellationReason.CouldNotShip and public OrderCancelled.Reason
CouldNotShip.

End-to-end, with a clock the test can move:
- a payment that never answers times out, cancels the order, releases the stock, and cancels the
  request at the provider;
- a late authorization after that changes nothing;
- a parcel that is never booked expires its reservation, cancels with CouldNotShip, and voids the
  hold, with no release.
```
