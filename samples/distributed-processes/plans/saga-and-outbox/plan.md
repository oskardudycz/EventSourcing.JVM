# E-commerce: one saga, event store as outbox, realistic settlement

Scope: `samples/distributed-processes`, package `ecommerce`. State: [todo.md](todo.md).

## What we agreed

1. One stateless `OrderSaga` with only a bus, `on(event)` grouped as Hold, Commit and Compensation. It consumes the Order's internal events and the public events of the other modules.
2. The event store is logically the outbox. `append` only writes. Subscribers read one global log, each from its own checkpoint. Delivery runs after the outermost append and is never nested. A failing subscriber keeps its checkpoint and gets the event again. Projections, forwarders, the saga and the provider clients all subscribe to the store.
3. Two abstractions: `EventStore` (append, read, subscribe) and one `MessageBus` for commands and public events.
4. Each forwarder updates its own summary, then publishes.
5. Capture is a request/answer seam, like authorization. A failed capture becomes `PaymentFailed`. The order has already shipped, so the client pays again (`RetryOrderPayment`); an order still unpaid at the end of the retry period is closed with `OrderClosedUnpaid`. (Changed on 2026-11-13, approved by Oskar; it was "stays open for a person".)
6. Void stays fire-and-forget, because an uncaptured hold lapses by itself. Refund goes, because nothing can trigger it. On payment timeout, the provider request is cancelled.
7. Timeouts are to-do lists with a `@Scheduled handleAll()`, as in the workshop. Tests call `handleAll()`. The only order-level timeout is the retry period for paying again. (Changed on 2026-11-13, approved by Oskar; it was "no order-level timeout".)
8. Stock committed to an order has no timer. A rejected fulfillment request cancels with "could not ship", not "out of stock". `PaymentAuthorizationRequested` carries `requestedAt`. (Changed on 2026-11-13, approved by Oskar; it was about an expired reservation.)
9. End-to-end tests assert full transcripts with one `MessageCatcher`.

## Open questions

- Q1 (step 3), answered: implement `subscribe` in `ESDBEventStore` on `ESDBSubscription` over `$all`, with an integration test against EventStoreDB from `docker compose`.
- Q2 (step 2), answered: `orders/OrderSaga.java`.

## Rules for every step

TDD. After each step, `./gradlew test` is green and the output is pristine (the only known compiler note is the older "unchecked operations" one). Tests assert behaviour, not log lines. EventStoreDB for `shoppingcarts/ShoppingCartTests` runs with `docker compose up -d eventstore.db`. Aggregates stay idempotent and never throw. Comments only for justified complexity; remove a comment only if it became false, and name it. Update the README for what the step changes. No git. All steps are sequential.

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

### 2b. Bump to Spring Boot 4.1

Done: Gradle 9.2.1, Spring Boot 4.1.2 BOM, `spring-boot-starter-log4j2` (fixes the SLF4J binding), Jackson 3, EventStoreDB client 5.4.5, JSpecify `@Nullable`, Java 22 kept. Tests log the sample's own output to `logs/test.log`.

### 3. Messaging: event store as outbox, one MessageBus

```text
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

Move subscribe and subscribeWithMetadata into EventStore. ESDBEventStore implements them on
ESDBSubscription over $all, with an integration test against EventStoreDB.
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

### 5. Timeouts, fulfilment and paying again

Agreed on 2026-11-13, after the research in [real-world-flow.md](real-world-flow.md).

```text
Shipments:
- No stock timer: remove reservedUntil, StockReservations, ReservationExpiryWorker,
  ExpireStockReservation, StockReservationExpired and ShipmentSummaries.
- Rename to the fulfilment request: SendPackage -> RequestFulfillment, ParcelBookingRequested ->
  FulfillmentRequested, ConfirmParcelBooking -> ConfirmPackageSent, DeliveryProvider(Client) ->
  FulfillmentService(Client).
- Add RejectFulfillment -> FulfillmentRejected; ReleaseStock works after a rejection.
- Public: StockReserved, PackageWasSent, ShipmentFailed(ProductsOutOfStock, FulfillmentRejected);
  no public PackageWasDelivered; no unread fields.

Payments:
- AuthorizePayment carries the paymentId.
- Public events carry referenceId, paymentId and the time only; PaymentSummary keeps referenceId.
- PaymentTimeoutWorker and AuthorizationExpiryWorker: handle(now), plus Scheduled variants with
  @Scheduled handleAll(), exposed by the module.

Orders:
- The order completes on sent and captured; no delivery recording.
- OrderPaymentDue names the payment to capture, after the dispatch and after a paid-again
  authorization.
- ShipmentFailed passes its reason: FulfillmentRejected cancels with CouldNotShip and releases the
  stock.
- RetryOrderPayment -> OrderPaymentRetried with a payment id derived from the order id and the
  attempt number.
- OrdersAwaitingPayment, UnpaidOrdersWorker and its Scheduled variant send CloseUnpaidOrder at the
  end of the retry period -> OrderClosedUnpaid.
- Remove PendingOrders.olderThan and PendingOrder.initializedAt.

End-to-end, with a clock the test can move:
- a payment that never answers times out, cancels the order, releases the stock and voids at the
  gateway; a late authorization after that changes nothing;
- a rejected fulfillment request cancels the order, voids the hold and releases the stock;
- the client paying again after a failed capture completes the order;
- an order still unpaid at the end of the retry period is closed unpaid, and not before.
```
