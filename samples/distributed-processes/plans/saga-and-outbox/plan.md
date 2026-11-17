# E-commerce: one saga, event store as outbox, realistic settlement

Scope: `samples/distributed-processes`, package `ecommerce`. State: [todo.md](todo.md).

## What we agreed

1. One stateless `OrderSaga` with only a bus, `on(event)` grouped as Hold, Commit and Compensation. It consumes the Order's internal events and the public events of the other modules.
2. The event store is logically the outbox. `append` only writes. Subscribers read one global log, each from its own checkpoint. Delivery runs after the outermost append and is never nested. A failing subscriber keeps its checkpoint and gets the event again. Projections, forwarders, the saga and the provider clients all subscribe to the store.
3. Two abstractions: `EventStore` (append, read, subscribe) and one `MessageBus` for commands and public events.
4. Each forwarder updates its own summary, then publishes.
5. Capture is a request/answer seam, like authorization. A failed capture becomes `PaymentFailed`. The order has already shipped, so the client pays again (`RetryOrderPayment`); an order still unpaid at the end of the retry period is closed with `OrderClosedUnpaid`. (Changed on 2026-11-13, approved by Oskar; it was "stays open for a person".) (Changed in step 7: the retry period is the `dueAt` that `PaymentTerms` gives at the first failure, and a payment that fails before confirmation also waits for the client, see step 7.)
6. Void stays fire-and-forget, because an uncaptured hold lapses by itself. Refund goes, because nothing can trigger it. On payment timeout, the provider request is cancelled.
7. Timeouts are to-do lists with a `@Scheduled handleAll()`, as in the workshop. Tests call `handleAll()`. The only order-level timeout is the retry period for paying again. (Changed on 2026-11-13, approved by Oskar; it was "no order-level timeout".) (Changed in step 7.1: the aggregates decide every deadline; the to-do lists only say whom to wake up and when.)
8. Stock committed to an order has no timer. A rejected fulfillment request cancels with "could not ship", not "out of stock". `PaymentAuthorizationRequested` carries `requestedAt`. (Changed on 2026-11-13, approved by Oskar; it was about an expired reservation.) (Changed in step 7.1: it also carries `timesOutAt`.)
9. End-to-end tests assert full transcripts with one `MessageCatcher`.

## Open questions

- Q1 (step 3), answered: implement `subscribe` in `ESDBEventStore` on `ESDBSubscription` over `$all`, with an integration test against EventStoreDB from `docker compose`.
- Q2 (step 2), answered: `orders/OrderSaga.java`.
- Q3 (step 7), answered: a sandbox gateway and a sandbox warehouse in `payments/sandbox` and `shipments/sandbox`.
- Q4 (step 7.4), answered on 2026-11-16: Springwolf generates the AsyncAPI document from the code. Spring services generate it this way (Springwolf's users include OTTO, PagoPA and LVM Versicherung); hand-written documents belong to design-first governance, as in the adidas guidelines. The details are in step 7.
- Q5 (step 8), open: KurrentDB or the workshop's `PostgreSQLEventStore`.
- Q6 (step 7.2), answered on 2026-11-16: the gateway sends the card hold's `expiresAt` in `ConfirmPaymentAuthorization`, as Stripe's `capture_before` does. The shop does not compute it.
- Q7 (step 8), open: whether `<module>-internal` stays the event-store subscription or also becomes a Kafka topic, and where the commands inside a module go (`RecordOrder…`, `TimeOutPayment`, the webhook commands): in-process calls or the module's internal channel.
- Q8 (step 7.2), answered on 2026-11-16: one model per side, Stripe for payments and Shopify for the shop, American spelling. See Names in step 7.
- Q9 (step 7.3), answered on 2026-11-16: pages for the whole e-commerce flow, server-rendered with Thymeleaf, so a person buys in the browser without curl, styled close to Stripe. "Pay now" moves under the order. Details in 7.3.
- Q10 (step 7.4), answered on 2026-11-16: the in-memory `MessageBus` gets the channels in step 7, starting from the routing in the event-driven architecture workshop's solution (`e03_businessprocesses/core`: `MessageRoutes`, `MessageRouter`, `RoutingTarget`). Details in 7.4.

## Rules for every step

TDD. After each step, `./gradlew test` is green and the output is pristine (the only known compiler note is the older "unchecked operations" one). Tests assert behaviour, not log lines. KurrentDB for `shoppingcarts/ShoppingCartTests` and `KurrentDBEventStoreTests` starts with Testcontainers (since the rebase onto main on 2026-11-16). Aggregates stay idempotent and never throw. Comments only for justified complexity; remove a comment only if it became false, and name it. Update the README for what the step changes. No git. All steps are sequential.

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

### 6. The shopping cart from real shops

Agreed on 2026-11-13, after the research in section 7 of [real-world-flow.md](real-world-flow.md#7-cart), and with the short streams of the Emmett D1 sample.

```text
Shopping carts:
- The client is logged in from the first product (Allegro).
- No OpenShoppingCart and no ShoppingCartOpened: the first ProductItemAddedToShoppingCart starts
  the cart.
- Short streams: ShoppingCartId.of(clientId, n). One open cart per client; ClientShoppingCarts
  knows the current one. The first product after a checkout starts cart n + 1. A lost race to
  start a cart is retried.
- AddProductItemToShoppingCart(clientId, productItem): the line keeps the first price of its
  product.
- RemoveProductItemFromShoppingCart(shoppingCartId, productItem, expectedVersion): the event
  records the cart's price.
- CheckOutShoppingCart(shoppingCartId, expectedVersion) reads the current prices:
  same -> ShoppingCartCheckedOut; changed -> ProductItemPriceChanged per product and no checkout.
  An empty cart is refused; a repeated checkout appends nothing.
- No cancel: CancelShoppingCart and ShoppingCartCanceled go.
- Public ShoppingCartFinalized -> ShoppingCartCheckedOut.
- The client calls ShoppingCartFacade directly, and the facade returns the ETag. The message bus
  keeps only messages between modules.
- RandomProductPriceCalculator keeps one price per product.

Core:
- AggregateStore throws ConcurrentModificationException on a version conflict, so a retry can tell
  it apart from a broken business rule.
- Without an expected version, AggregateStore retries a conflict up to 3 times on the new state.
  With an expected version (a person's command), the conflict goes back to the caller.
```

### 7. The customer's side and the contracts, in memory

Agreed on 2026-11-13, after the research in section 8 of [real-world-flow.md](real-world-flow.md#8-payment-page-and-the-clients-pages). Updated on 2026-11-16 with Q4 and Q6 and with what 7.1 built. Everything stays in memory; the real infrastructure is step 8. Oskar asked for the whole step in one go: the phases run one after another without a stop, and each one ends green.

```text
Scope:
- Only the ecommerce modules. An endpoint only for what a person does. No webhook endpoints.
- Shipments gets no api package: no person sends it a command.
- The customer id comes from the URL: /customers/{customerId}/...
- New libraries in their latest stable version. No Gradle bump unless required.
```

#### Names

Decided on 2026-11-16: one model per side, made consistent, not a clone.

```text
Rule: Stripe for the payment side, Shopify for the shop side, American spelling everywhere. The
models give the names at the gateway seam and on the customer's pages. The domain commands and
events keep the sample's own language where the model has no term.

Payments (Stripe, PaymentIntent with manual capture and a hosted Checkout Session):
- CheckoutSession(url, expiresAt), CreateCheckoutSession(paymentId),
  CheckoutSessionCreated(paymentId, url, expiresAt). The shop sets the session's expiresAt:
  30 minutes. An open session is reused until it expires; after that, a new session for the
  same payment (Stripe's recovery does the same).
- successUrl and cancelUrl, both the order status page.
- captureBefore: the hold's deadline in ConfirmPaymentAuthorization and PaymentAuthorized.
- CancelPayment, PaymentCanceled, PaymentGateway.cancel (were VoidPayment, PaymentVoided,
  voidAuthorization).
- No final decline: a declined card fails on the gateway's page and the customer tries again
  there. DeclinePayment, PaymentDeclined and DeclineReason go.
- The payment's own deadline stays the shop's rule: PaymentValidity, 24 hours (Stripe's default
  Checkout Session lifetime). When the payment times out, the gateway expires its open session.
- PaymentGateway: createCheckoutSession(paymentId, amount, successUrl, cancelUrl, expiresAt),
  expireCheckoutSession(paymentId), capture(paymentId, amount), cancel(paymentId).

Shop (Shopify):
- customer, not client: customerId, CustomerShoppingCarts, /customers/{customerId}/...
- Pages and views: Products, Cart (Carts, document Cart), Orders (CustomerOrders, document
  CustomerOrder), Order status (OrderStatuses, document OrderStatus).
- Buttons: "Add to cart", "Check out", "Pay now", "Retry payment", "Cancel order".
- PaymentTerms and dueAt (Shopify's payment terms); PaymentTerms.within(7 days) is Shopify's
  "Within 7 days" template.
- OrderClosedUnpaid is Shopify's order close.

Spelling: canceled, canceling (OrderCanceled, PaymentCanceled), but cancellation
(OrderCancellationReason); authorization; fulfillment. The README follows.
```

#### Topology

Agreed on 2026-11-16, after [Internal and external events](https://event-driven.io/en/internal_external_events/) and [Predictable identifiers](https://event-driven.io/en/predictable-identifiers-enabling/). Showing a proper topology is the point of the sample: never a channel per message type.

```text
Naming: <module>-<kind>, with the modules shopping_carts, orders, payments, shipments.

Module          Internal                 External events           Requests (in)        Responses (out)       Its own response queues
shopping carts  shopping_carts-internal  shopping_carts-external   -                    -                     -
                                         ShoppingCartCheckedOut
orders          orders-internal          -                         -                    -                     orders-payments-responses,
                                                                                                              orders-shipments-responses
payments        payments-internal        -                         payments-requests    payments-responses    -
                                                                   AuthorizePayment     PaymentAuthorized
                                                                   CapturePayment       PaymentCaptured
                                                                   VoidPayment          PaymentFailed
shipments       shipments-internal       -                         shipments-requests   shipments-responses   -
                                                                   ReserveStock         StockReserved
                                                                   RequestFulfillment   PackageWasSent
                                                                   ReleaseStock         ShipmentFailed

- Internal: the module's own events for its own subscribers (projections, forwarders, gateway
  clients, OrderSaga for the order's events). Today it is the module's event-store subscription.
- External events: enriched summary events for every other module, as facts nobody asked for. The
  forwarder appends them to the module's external stream, which is forwarded to the channel.
  Orders subscribes to shopping_carts-external directly; no routing.
- Requests and responses: the module that does the work owns <module>-requests and
  <module>-responses and does not know who asks. The module that asks owns its response queue.
- A request carries the requester's URN as the correlation, built by the requester from its own
  id: urn:ecommerce:order:<uuid>. The response's routing key comes from it:
  ecommerce.order.<uuid>. orders-payments-responses and orders-shipments-responses are bound with
  ecommerce.order.*.
- A message travels: OrderSaga sends AuthorizePayment to payments-requests -> payments publishes
  PaymentAuthorized to payments-responses with key ecommerce.order.<uuid> -> the routing delivers
  it to orders-payments-responses -> OrderSaga.
- Orders has no external channel: no module reads its events (step 4).
```

#### 7.1 Deadlines in the aggregates, policies in the facades (done on 2026-11-16)

```text
Built:
- PaymentTerms (orders, Shopify's name): dueAt(failedAt). PaymentTerms.within(Duration) is the
  only kind for now. OrderFacade runs it and gives dueAt to Order.recordPaymentFailure.
- PaymentValidity (payments, PayU's validityTime): timesOutAt(requestedAt).
  PaymentValidity.of(Duration). PaymentFacade runs it when the authorisation is requested.
- They replace paymentRetryPeriod and paymentTimeout in ECommerceConfig, OrdersConfig and
  PaymentsConfig.
- OrderPaymentFailed(orderId, failedAt, dueAt). The order keeps the first dueAt; a retry that fails
  again keeps it. closeUnpaid(now) appends nothing before dueAt.
- PaymentAuthorizationRequested(paymentId, referenceId, amount, requestedAt, timesOutAt).
  Payment keeps timesOutAt and expiresAt; timeOut(now) and expireAuthorization(now) append nothing
  before them.
- The to-do lists hold only whom to wake up and when, and include the exact instant:
  PendingPayments.timedOutAt(now), AuthorizedPayments.expiredAt(now),
  OrdersAwaitingPayment.overdueAt(now). The workers take no Duration.
- OrdersAwaitingPayment starts at OrderPaymentFailed and ends at OrderCompleted,
  OrderClosedUnpaid or OrderCancelled. Until 7.2, a failure before shipping passes through it on
  its way to OrderCancelled.
- Tests: OrdersAwaitingPaymentTests (new); early wake-ups in OrderTests, OrderFacadeTests,
  PaymentTests, PaymentFacadeTests; AuthorizationGatewayClientTests time out at the deadline.
```

#### 7.2 Names, the Checkout Session, the unpaid order, the customer's order commands

```text
Renames first, behaviour unchanged, all tests green (the Names section):
- client -> customer (clientId, ClientShoppingCart(s), the URL segment later in 7.3).
- VoidPayment, PaymentVoided, voidAuthorization -> CancelPayment, PaymentCanceled, cancel.
- OrderCancelled -> OrderCanceled. The README: canceled, authorization, fulfillment.
- PaymentValidity loses the PayU reference; 24 hours.

Gateway contract:
- PaymentGateway.authorize goes. createCheckoutSession(paymentId, amount, successUrl, cancelUrl,
  expiresAt) returns CheckoutSession(url, expiresAt). expireCheckoutSession(paymentId), capture
  and cancel.
- ConfirmPaymentAuthorization(paymentId, captureBefore): the webhook brings the hold's deadline
  (Q6, Stripe's capture_before). PaymentAuthorized(authorizedAt, captureBefore). PaymentFacade and
  the configs lose authorizationValidity.
- DeclinePayment, PaymentDeclined and DeclineReason go: a declined card is not final in Stripe.

Payment:
- AuthorizePayment only records PaymentAuthorizationRequested. Nothing calls the gateway until the
  customer clicks "Pay now". AuthorizationGatewayClient no longer handles
  PaymentAuthorizationRequested; it cancels at the gateway on PaymentCanceled and expires the open
  session on PaymentTimedOut.
- CheckoutSessionCreated(paymentId, url, expiresAt), internal, not public. Payment keeps the
  open session.
- PaymentFacade.createCheckoutSession(CreateCheckoutSession(paymentId, referenceId, successUrl,
  cancelUrl)) is called by the web API, not through the bus, because the customer waits for the
  URL. The URLs come with the request, as in Stripe's API:
  - no payment, or a payment for another referenceId: nothing (the API answers 404);
  - not pending any more (authorized, failed): nothing (409);
  - pending with a session that has not expired: that session, nothing appended;
  - pending without one, or with an expired one: the gateway creates a session for the same
    payment, expiring in 30 minutes; CheckoutSessionCreated is appended and the session goes back
    (303 to its url);
  - the gateway throws: the exception goes up, nothing is recorded (the API answers 502).
  The aggregate decides: recording a session appends nothing unless the payment is pending and has
  no open session.
- The session's expiresAt is the session's. The payment still times out at timesOutAt.

Order, unpaid before confirmation:
- OrderPlaced still sends AuthorizePayment and ReserveStock at once.
- recordPaymentFailure while Opened: OrderPaymentFailed(failedAt, dueAt). The order stays open
  and keeps its stock reservation.
- A stock failure cancels, as now. While the payment (first or retried) is still pending, the
  order waits for its answer, because that answer decides whether a hold must be canceled. If both
  failed, the reason stays PaymentFailed.
- retryPayment: Opened with a failed payment and no failed shipment, or Confirmed, shipped, with
  a failed payment -> OrderPaymentRetried with the next payment id.
- recordPaymentAuthorization: Opened and Pending (first payment) or Opened and Retrying with the
  retried payment id -> OrderPaymentAuthorized, and OrderConfirmed when the stock is reserved.
  Confirmed and Retrying -> OrderPaymentAuthorized and OrderPaymentDue (no change).
- After OrderConfirmed and before the package is sent, a lapsed hold still cancels (no change).
- closeUnpaid(now): before dueAt nothing. Opened, failed or retrying ->
  OrderCanceled(PaymentFailed): the compensation releases the stock, and a retried payment that
  is still pending times out by itself. Confirmed and shipped -> OrderClosedUnpaid.
- cancel(Requested) works while Opened, so also for an unpaid order.

Orders module:
- CancelOrder and RetryOrderPayment leave the message bus; the web API calls OrderFacade
  directly. The order still never throws; both answer 202.

Test doubles:
- The doubles stand for the gateway's server: they create sessions and answer captures. The
  customer paying on the gateway's page and the webhook are steps in the tests: call
  PaymentFacade.createCheckoutSession, then publish ConfirmPaymentAuthorization(paymentId,
  captureBefore).
- The doubles' authorize answers go. AutoRejectingPaymentGateway is deleted. ThrowingPaymentGateway
  also throws on createCheckoutSession.
- CreateCheckoutSession is not on the bus, so the transcripts start at the webhook command.

ECommerceTests:
- check out, "Pay now", the webhook confirms -> confirmed and fulfillment requested;
- "Pay now" twice before the session expires -> one session; after it expires -> a new session
  for the same payment;
- a customer who never pays -> timed out, the session expired at the gateway, unpaid; "Retry
  payment" and paying the new payment -> confirmed;
- at dueAt still unpaid -> canceled and the stock released;
- out of stock while unpaid -> canceled;
- the customer cancels an unpaid order -> canceled and the stock released;
- "Pay now" on a payment that is not pending any more -> no session;
- the gateway fails to create the session -> the call throws, nothing recorded;
- the scenarios after shipping keep their behavior.

README: the names, the three lines about the old behavior before shipping (a failed payment
cancels; "Retry payment" only after shipping; the gap bullet), the payment flow, the gateway
contract and captureBefore from the webhook.
```

#### 7.3 The customer's views, the web API with Swagger, the shop pages, the sandbox (done on 2026-11-16)

```text
Wiring:
- A Spring @Configuration in the ecommerce package builds ECommerceModule over an
  InMemoryEventStore and an InMemoryMessageBus, with the sandbox gateway and warehouse, the
  existing RandomProductPriceCalculator bean, PaymentTerms.within(7 days) (Shopify's "Within 7
  days") and PaymentValidity.of(24 hours). The sandbox hold lasts 7 days (Stripe's usual card
  hold).
- The facades and views become beans for the controllers and the pages.
- Dependencies: spring-boot-starter-webmvc and spring-boot-starter-thymeleaf (managed by Spring
  Boot 4.1.2), springdoc-openapi-starter-webmvc-ui 3.2.0, and the Spring Boot test starter.

Views, one per page, no reuse and no extension (in each module's api package):
- Carts (Cart): lines, unit prices, total, version. Read through CustomerShoppingCarts' current
  cart.
- CustomerOrders (Orders): order id, placed at, total, status.
- OrderStatus (Order status): items, total, status, carrier and tracking number, the current
  payment id, and what the customer can do now: "Pay now" while the payment is pending, "Cancel
  order" while the order is open and not confirmed, "Retry payment" while a payment has failed and
  the order can still be paid. The first payment id is derived from the order id, as the saga
  does; a retry's comes in OrderPaymentRetried.

Endpoints (each calls its module's facade or view directly):
- GET    /customers/{customerId}/shopping-carts/current                 Cart
- POST   /customers/{customerId}/shopping-carts/current/product-items   AddProductItemToShoppingCart
- DELETE /customers/{customerId}/shopping-carts/current/product-items?productId=&quantity=
                                                                        RemoveProductItemFromShoppingCart, If-Match
- POST   /customers/{customerId}/shopping-carts/current/checkout        CheckOutShoppingCart, If-Match
- GET    /customers/{customerId}/orders                                 CustomerOrders
- GET    /customers/{customerId}/orders/{orderId}                       OrderStatus
- POST   /customers/{customerId}/orders/{orderId}/cancel                CancelOrder, 202
- POST   /customers/{customerId}/orders/{orderId}/retry-payment         RetryOrderPayment, 202
- POST   /customers/{customerId}/orders/{orderId}/payments/{paymentId}/checkout-session
                                                                        CreateCheckoutSession, 303
- The checkout-session endpoint builds successUrl and cancelUrl from its own path: the order
  status page. No lookup, nothing for payments to know about orders.
- Cart commands answer with the cart's ETag. A checkout that finds a changed price answers with the
  new ETag; a checkout that goes through answers with Location: the order status page.
- Errors, in one @RestControllerAdvice: 412 for a version conflict (ConcurrentModificationException),
  409 for a broken rule (the cart's IllegalStateException, a payment that is not pending), 404 for a
  missing cart, order or payment, 502 when the gateway fails to create a session.
- Swagger UI with springdoc for the JSON API.

Shop pages (Q9), server-rendered with Thymeleaf, for the whole flow, so a person buys in the
browser:
- Products: a few fixed demo products (the sample has no catalog), each with "Add to cart".
- Cart: items, unit prices, total; remove, "Check out". A changed price shows the new price and
  asks again, as the API's checkout does.
- Orders: the customer's orders with their status.
- Order status: items, total, status, carrier and tracking number, and the buttons the customer
  can use now: "Pay now", "Cancel order", "Retry payment". It is the successUrl and the cancelUrl.
- "Pay now" is a form post to the checkout-session endpoint, which answers 303 to the sandbox
  gateway's page; the gateway sends the browser back to Order status.
- The pages call the same facades and views as the JSON API.
- Style: clean and close to Stripe: white cards on a light grey background, one accent color for
  primary buttons, clear status badges. The sandbox gateway's page close to Stripe Checkout: the
  order summary on the left, the card form on the right, "Pay" as the primary button, a "Back"
  link to the cancelUrl. Plain CSS in static resources, no framework, no fonts from outside.
- The customer id comes from the URL, as in the API; the Products page starts a new customer id.

Sandbox, for running the application (the tests keep their own doubles):
- payments/sandbox: SandboxPaymentGateway creates sessions that point to its own payment page,
  served by the application, with a card form. Stripe's test cards: 4242 4242 4242 4242 pays,
  4000 0000 0000 0002 is declined and the page says so, and the customer can try again. Paying
  publishes ConfirmPaymentAuthorization(paymentId, captureBefore), as the webhook would, and
  sends the customer to the successUrl with a 303. "Back" goes to the cancelUrl. Capture answers
  ConfirmPaymentCapture at once; cancel and expiring a session do not answer.
- shipments/sandbox: the sandbox warehouse answers a fulfillment request with ConfirmPackageSent,
  with a sandbox carrier and tracking number.

Tests:
- the three views as projections;
- one HTTP test per endpoint, with the status codes and headers above;
- the sandbox page: paying, a declined card, "Back";
- one purchase over HTTP end to end: add a product, check out, "Pay now", the sandbox pays, the
  warehouse sends the package, the capture -> OrderStatus shows the order completed;
- the pages: each page renders for its state, and the same purchase through the pages' forms and
  redirects.

README: the web API section, the shop pages, Swagger, the sandbox and how to run the
application.
```

#### 7.4 Channels in the bus, AsyncAPI with Springwolf, and EventCatalog (done on 2026-11-16)

```text
Channels in the in-memory bus (Q10), from the workshop's e03_businessprocesses/core:
- Bring MessageRoutes, MessageRouter and RoutingTarget into core/messaging, with their tests
  (MessageRouterTest). Each module registers its routes: a message's target channel and key, by
  class, interface or package (inPackage).
- Publishing: the bus asks the router for (channel, key) and puts the message on that channel.
  The convention for a message without a route: <module>-internal, from its package.
- The routes, as in the Topology section: requests to <module>-requests; responses to
  <module>-responses with the key from the requester's URN (urn:ecommerce:order:<uuid> ->
  ecommerce.order.<uuid>); the cart's public events to shopping_carts-external.
- What the workshop does not have: the consuming side. Its RabbitMQ variant binds one queue to the
  exchange with "#", and its Kafka variant reads one topic; both dispatch by type. Our bus adds
  bindings: a queue bound to a channel with a pattern (topic-exchange rules: * one segment, # any),
  and subscribe(queue, type, handler). orders-payments-responses and orders-shipments-responses are
  bound to the responses channels with ecommerce.order.*.
- The MessageCatcher still sees every message, now with its channel.
- Tests first: routing by route and by convention; a response reaches only the queue whose pattern
  matches its key; a requester's queue gets nothing meant for another requester.

AsyncAPI:
- springwolf-core and springwolf-ui 2.9.0 (built on Spring Boot 4.1.1). Springwolf brings
  spring-boot-jackson2, so Jackson 2 sits next to our Jackson 3; check that it changes nothing in
  our serialisation.
- AsyncAPI 3.0 for the channels in the Topology section above, all of them, internal ones included:
  the external channels are the contract for the other modules, and the internal channel is
  documented for the team that owns the module. Each channel lists the messages it carries.
- The requests carry the correlation URN, and the responses' routing key is described with it.
- No servers yet; in step 8 the channels become the Kafka topics, so the contract does not change.
- The bus is our own, so Springwolf finds nothing by itself. The methods carry the annotations:
  - a PaymentFacade or ShipmentFacade method that handles a request from <module>-requests:
    @AsyncListener;
  - a forwarder method that publishes to <module>-responses or <module>-external:
    @AsyncPublisher;
  - an OrderSaga method: @AsyncListener for what it reads (shopping_carts-external,
    orders-payments-responses, orders-shipments-responses, orders-internal), and @AsyncPublisher
    for the requests it sends to payments-requests or shipments-requests;
  - the internal subscribers (projections, forwarders, gateway clients, OrderSaga for the order's
    own events): @AsyncListener on <module>-internal.
  Find out how Springwolf 2.9.0 finds annotated classes (component scan or beans) before wiring.
- One Springwolf group per module, so each module has its own document.
- UI at /springwolf/asyncapi-ui.html; the documents at /springwolf/docs.
- Test: each module's document has exactly its channels from the Topology section, each with
  exactly its messages.

EventCatalog:
- EventCatalog 5.12.0 with @eventcatalog/generator-openapi 9.1.2 and
  @eventcatalog/generator-asyncapi 7.1.2, in samples/distributed-processes/eventcatalog (Node 24 is
  installed).
- One domain, E-Commerce; one service per module (shopping carts, orders, payments, shipments).
  springdoc groups per module give each service its OpenAPI document, and the Springwolf groups
  give its AsyncAPI document.
- The documents are exported from the running application to files the generators read: the
  springdoc Gradle plugin (latest stable) with one URL per group, if it can fetch the Springwolf
  URLs too; otherwise a test that writes them.
- Test: the catalog builds from the exported documents.

README: the contracts section, how to open the Springwolf UI and how to build the catalog.
```

What was built, where it differs from the text above:

```text
Channels:
- The convention gives no route. Each module routes its own package to <module>-internal with
  MessageRoutes.inPackage. A message without a route reaches only the middleware; no queue gets it.
- The MessageCatcher catches the message, not its channel. The routing tests
  (MessageRouterTests, InMemoryMessageBusTests) check the channels and the keys.
- The internal channels carry the module's own commands, received by its facade:
  orders-internal (PlaceOrder, RecordOrder..., CloseUnpaidOrder), payments-internal (the webhook
  commands, TimeOutPayment, ExpirePaymentAuthorization, FailPaymentCapture) and
  shipments-internal (the warehouse's commands). The projections, forwarders, gateway clients and
  the saga's order-event handlers read the event store, not the bus, so they are not on a channel.
  Q7 decides in step 8 whether that changes.
- PaymentExternalEvent and ShipmentExternalEvent declare referenceId(), so one route covers each
  module's responses.

AsyncAPI:
- Springwolf 2.9.0 writes AsyncAPI 3.1.0, not 3.0.
- Springwolf finds annotated methods only on Spring beans. MessageBusClassScanner (a
  SpringwolfClassScanner bean) adds the facades, the forwarders and OrderSaga.
- Springwolf's groups filter by channel name, so payments' group would also take the saga's sends
  to payments-requests. ModuleOperations (an OperationCustomizer) marks each operation with x-module,
  from the package of the method's class, and ModuleGroupingService (a GroupingService) keeps only
  that module's operations and the channels they use. So orders' document has what the saga sends
  to payments-requests, and payments' document has what the facade receives there.
- EventCatalogMessageTypes (an AsyncApiCustomizer) sets x-eventcatalog-message-type: command for the
  records of OrderCommand, PaymentCommand and ShipmentCommand, event for the rest.
  eventCatalogOperationTypes (a springdoc GlobalOperationCustomizer) does the same for the web API:
  GET is a query, every other method a command.
- Jackson 2 from Springwolf sits next to our Jackson 3; the API tests show the JSON is unchanged.
- AsyncApiTests check each module's channels and messages, that each document has only its
  module's operations, the message types, the version 3.1.0 and the UI.

Export and EventCatalog:
- The springdoc Gradle plugin 2.1.0 fetches the Springwolf URLs too: ./gradlew generateOpenApiDocs
  starts the application on port 18086 and writes six files to eventcatalog/specs (two OpenAPI,
  four AsyncAPI). The files are kept in the repository, so the catalog builds without the
  application.
- The catalog follows the official create-eventcatalog template: its npm scripts (generate, dev,
  build, preview, plus test) and its .gitignore. The generated domains/ folder is kept in the
  repository, as the template says ("Commit the MDX and schema files to Git").
- teams/ has one team per module, users/ one member per team (names from Back to the Future), and
  each service's owners in eventcatalog.config.js names its team. Without them, the build warned
  about empty teams and users.
- repositoryUrl is the GitHub repository (the header links to it) and homepageLink is
  event-driven.io. EventCatalog uses homepageLink as Astro's site, and its link check treats every
  link on that host as a catalog page. With homepageLink on github.com, EventCatalog's own GitHub
  links in the header showed as 2 broken links.
- The build still warns about Vite's chunk size and the /api/settings routes. Both come from
  EventCatalog: the settings routes are prerendered but have only POST, and its Astro config sets no
  chunk size limit and has no option for one.
- The generators keep what a page in domains/ already has (owners, renamed or removed messages).
  A renamed message stays next to the new one until its folder is deleted.
- @Tag on ShoppingCartsController and OrdersController names the module, instead of springdoc's
  default "orders-controller", which EventCatalog showed as a badge.
- Test: Playwright 1.65.1 on the built catalog (tests/catalog.spec.js, 26 tests). It checks the home
  page counts (1 domain, 4 services, 38 messages), the domain's services, each service's outbound and
  inbound messages and its owning team with its member, each service's diagram, each specification page (the
  endpoints, or every channel), the teams directory, the header link to the repository, the listings (28 commands, 7 events with their
  producer and consumer, 3 queries), the domain visualiser, and no browser errors on any page.

API tests:
- The web API tests use ApiSpecification from the introduction to Event Sourcing workshop
  (given/when/then over RestTestClient on a random port), one class per endpoint, with
  PurchaseRestBuilder to bring an order to the state a test needs. They replace the MockMvc API
  tests. PurchaseOverHttpTests runs the whole purchase.
- ProductItem rejects a missing productId ("Product id is required"), found by those tests.
```

#### 7.5 A clean Springwolf UI, a login, the catalog from Gradle, and a how-to (done on 2026-11-17)

Asked by Oskar on 2026-11-17, after he went through the README and the running application.

```text
Springwolf (AsyncAPI):
- Every operation has a description in @AsyncOperation, and every message one in @Schema on its
  record. Springwolf copies a payload's description to its message only for inline payloads, so
  MessageDescriptions (an AsyncApiCustomizer) copies it for our records, which are referenced.
- Each module's document describes the module (springwolf.docket.group-configs[i].info.description).
- The documents name a server with the protocol in-memory. Springwolf's UI also needs a protocol on
  each operation and message, or it shows "unsupported-protocol" and a "No binding defined" warning.
  AsyncAPI allows only its known protocols as binding names, and others only with the x- prefix
  (with "in-memory", EventCatalog's parser rejected all four documents and still exited with 0), so
  InMemoryBindings adds an x-in-memory binding.
- Publishing from the UI: InMemoryPublishingController extends Springwolf's PublishingBaseController
  at /springwolf/plugin/x-in-memory/publish and publishes on our MessageBus. Without it, the UI's
  check of that endpoint was a 404 in the browser. Springwolf loads the payload class with
  Class.forName, which fails for records nested in their sealed interface, so
  NestedRecordPayloadCreator (Springwolf allows replacing the bean) loads them with ClassUtils.
- Springwolf names an operation channel_action_method and merges operations with the same name; it
  has no attribute for the name. The saga's on(...) overloads merged into one operation per channel
  (orders-internal_send_on had 7 messages). The saga's handlers are now named after their events:
  onShoppingCartCheckedOut, onOrderPlaced, onPaymentAuthorized, and so on. Still one class.
- The descriptions say "authorized" (the provider keeps the amount for the shop) and "charged" (the
  capture), with no banks.
- Tests: AsyncApiTests check the server and the binding on every operation and message, one message
  per operation, the saga's operation names, a description on every operation, message and module.
  SpringwolfPublishingTests check the publish endpoint and that a published message reaches the bus.

The shop:
- A dummy login: /shop asks for a name when no customer cookie is set. The customer id is derived
  from the name (UUID.nameUUIDFromBytes), so the same name opens the same orders. The header shows
  the name and "Log out", which clears the cookie. Before, /shop made a new random customer on every
  visit. Tests in ShopPagesTests.
- Prices are in whole cents: RandomProductPriceCalculator gives cents, and PricedProductItem and
  ProductItems.totalPrice round to cents. Money stays a double (Oskar's choice); the README says so.

EventCatalog from Gradle:
- The node-gradle plugin 7.1.0 downloads Node.js 26.13.1 and runs the catalog's npm scripts:
  eventCatalogGenerate (after generateOpenApiDocs), eventCatalog, eventCatalogPreview,
  eventCatalogTest and eventCatalogScreenshots. They are separate tasks; ./gradlew build stays
  without Node.js.
- domains/ was generated again from an empty folder, because the operations and descriptions
  changed.

Screenshots and the how-to:
- ShopInBrowserTests (Playwright for Java 1.65.0) runs in ./gradlew build: it logs in, buys two
  products, pays on the sandbox page, and opens Swagger UI and Springwolf UI, with no browser
  errors. It saves screenshots to build/screenshots; ./gradlew howToScreenshots saves them to
  assets/howto instead. The catalog's Playwright tests save three more with
  ./gradlew eventCatalogScreenshots.
- HOWTO.md: step by step through the shop, a failed payment published from Springwolf UI, Swagger
  UI, Springwolf UI, EventCatalog and the tests, with the screenshots.
- README.md rewritten in the order what, why, how: the purchase from the customer's side, reserve
  first and charge last, where the steps come from, the order and the saga, failures, messages
  between modules, reliable delivery, the outside systems and the sandboxes, the web API and the
  shop, and documenting the messages (what OpenAPI, AsyncAPI and EventCatalog are, why a
  message-based system needs them, how the sample builds them).
```

### 8. Real infrastructure

```text
- Kafka (latest stable kafka-clients, spring-kafka managed by Spring Boot), with the topics from the
  Topology section of step 7. The AsyncAPI documents get the Kafka servers.
- Kafka has no exchange and no pattern binding, so a routing consumer does that job: it reads
  payments-responses and shipments-responses, matches the URN in the key against each requester's
  pattern (ecommerce.order.* for orders), and writes to that requester's topic
  (orders-payments-responses, orders-shipments-responses).
- Orders reads shopping_carts-external with its own consumer group.
- The event store: KurrentDB (io.kurrent:kurrentdb-client 1.2.1, already in the build since main)
  or the PostgreSQLEventStore from workshops/build-your-own-event-store. Either needs a global log
  position and subscriptions with a checkpoint.
- JPA projections in place of the in-memory Database.
- In memory stays the default for the unit tests.
```
