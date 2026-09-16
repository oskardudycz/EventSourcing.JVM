# TODO — ECommerce distributed process

Prompts and detail live in [plan.md](plan.md). Decisions live in [spec.md](spec.md); the reasoning
behind them is in [qa.md](qa.md).

Working directory for every command: `samples/distributed-processes`.
**A step is done only when `./gradlew build` is green** — compile, linter, all tests, clean output.

---

## Phase 0 — Core seams  *(sequential — blocks everything)*

- [x] **0.1** `core/messaging`: `EventBus`, `CommandBus`, `IntegrationEventBus`, `InternalEventBus`
  - [x] in-memory command and integration buses, depth-first dispatch
  - [x] duplicate command handler throws; unhandled command throws
  - [x] bus tests
- [x] **0.2** `EventStore` interface, `ESDBEventStore`, `InMemoryEventStore`
  - [x] read the workshop model first: `introduction-to-event-sourcing/solved/.../e13_entities_definition/core/EventStore.java`
  - [x] append both persists **and** dispatches — no store-then-publish gap
  - [x] JSON envelopes, same Jackson config as the workshop
  - [x] expected revisions on top of the workshop version: `StreamAlreadyExists`, `Conflict`
  - [x] `ReadResult.Success` carries `Object[]` instead of `ResolvedEvent[]`
  - [x] `InMemoryEventStoreTests`
- [x] **0.3** `AggregateStore` on `EventStore` + version fix
  - [x] constructor takes `EventStore` (no compatibility constructor — nothing constructs it)
  - [x] `get` sets `version` during replay
  - [x] `AggregateStoreTests`
- [x] **0.4** Test helpers
  - [x] `AggregateSpecification` — `EventSourcedSpecification` is decider-shaped and cannot do this
  - [x] `MessageCatcher` with a readable transcript on failure
  - [x] proven against `ShoppingCart`

## Phase 1 — Modules  *(four parallel tracks, after Phase 0)*

### Track A — shoppingcarts
- [x] **A.1** `ShoppingCartFacade` (command records, no publishing) + `ShoppingCartsConfig` + tests
- [x] **A.2** cart forwarder on the integration bus + tests *(the only forwarder that re-reads)*

### Track B — orders
- [x] **B.1** defects + `OrderTests`
  - [x] `cancel` guard inverted — an opened order can never be cancelled
  - [x] `when(OrderInitialized)` never sets `totalPrice`
  - [x] PascalCase components in `OrderCommand` / `OrderCancelled`
  - [x] `InitializeOrder` uses the wrong `PricedProductItem`; add `orderId` + `cartId`
  - [x] `mapToStreamId`; `OrderCancellationReason` gains `PaymentFailed`, `Requested`
- [x] **B.2** `OrderFacade` (replaces the empty `OrderService`) + `OrdersConfig` + tests
- [x] **B.3** `OrderExternalEvent` + forwarder, wired + tests

### Track C — payments
- [x] **C.1** `mapToStreamId` + `PaymentTests` (including the settle-exactly-once guards)
- [x] **C.2** `PaymentFacade` (replaces the empty `PaymentService`) + `PaymentsConfig` + tests
- [x] **C.3** `PaymentGateway` + `PaymentGatewayClient` + four test doubles
  - [x] auto-completing, auto-rejecting, throwing, silent
  - [x] a thrown charge becomes `PaymentDiscarded`, never a propagated exception
- [x] **C.4** payment forwarder on the integration bus + tests
- [x] **C.5** `PendingPayments` + `PaymentTimeoutWorker.run(now)` + tests

### Track D — shipments
- [x] **D.1** defects + delivery + `ShipmentTests`
  - [x] delete the copy-pasted `shipments/PaymentService.java`
  - [x] camelCase `ShipmentCommand`; `SendPackage` gains `shipmentId`
  - [x] static factory, `mapToStreamId`, `when` sets `orderId`
  - [x] `PackageWasDelivered` + `deliver(now)` with guards
- [x] **D.2** `ShipmentFacade` (both commands) + `ShipmentsConfig` + tests
- [x] **D.3** `DeliveryProvider` + `DeliveryProviderClient` + two test doubles
- [x] **D.4** `ShipmentExternalEvent` + forwarder, wired + tests

Phase 1 is green at **123 tests**, 0 failures, 0 skipped (Phase 0 ended at 48). Steps 2.0c to 2.0g
leave the build green at **180 tests**.

## Phase 2 — Wiring  *(sequential — needs all of Phase 1)*

- [x] **2.0** `core/identifiers/Urn` + `AggregateStore.addIfAbsent`
  - [x] URNs are JOINED strings: `urn:ecommerce:order:<tail>` → `urn:ecommerce:payment:<tail>`
  - [x] `add` stays strict; the tolerance is a named method the handler chooses
  - [x] `addIfAbsent` must not read-then-write — that is a race
  - [x] `DeterministicUuid` deleted — hashing was opaque and was the wrong technique
- [x] **2.0b** typed URN identifiers across ecommerce — `UUID` → `ShoppingCartId`/`OrderId`/`PaymentId`/`ShipmentId`
  - [x] `mapToStreamId` maps the id to `<Entity>-<tail>`, keeping the ESDB `$ce-` category clean
  - [x] one `getAndUpdate` path; idempotency moved into each aggregate's create guard
  - [x] `Payment` and `Shipment` hold an opaque `String referenceId`, never an `orderId`
  - [x] Jackson 2.18.2; `OrderIdSerializationTests` pins the `@JsonValue` behaviour
  - [x] `clientId` and `productId` stay `UUID` — nothing derives them
- [x] **2.0c** gateway-shaped commands — `RequestPayment(referenceId, amount)` and
      `SendPackage(referenceId, productItems)`
  - [x] the caller never names the payment or the shipment, as with Adyen and Stripe
  - [x] `PaymentFacade` and `ShipmentFacade` derive their own id from the reference
  - [x] the notification carries both, like a webhook: `pspReference` + `merchantReference`
  - [x] `OrderSaga` no longer names `PaymentId` or `ShipmentId`
- [x] **2.0d** `Order` waits for outcomes instead of following a sequence, like `GroupCheckout`
  - [x] one `Outcome` per participant; the second outcome to arrive finalises the order
  - [x] `OrderPaymentFailed`, `OrderShipmentRecorded`, `OrderShipmentFailed` added
  - [x] `CompleteOrder` deleted — nobody outside the order decides it is done
  - [x] `OrderInitialized` carries the `cartId` it came from
  - [x] the saga asks for payment and shipment together
  - [x] refund guard is now the null `paymentId` alone
- [x] **2.0e** no aggregate throws at a message it cannot use
  - [x] `Order`, `Payment` and `Shipment` append nothing and return instead of throwing
  - [x] `ShoppingCart` still throws — its commands come from a person, not a message
  - [x] `AggregateSpecification.thenNothing()` for the idempotency cases
- [x] **2.0f** naming, on review
  - [x] `when` → `evolve` on every aggregate, including `hotelmanagement`
  - [x] `mapToStreamId` moved from each aggregate to its facade
  - [x] `PaymentExternalEvent.PaymentFailed.Reason` made public — consumers could not read it
- [x] **2.1** `OrderSaga` rewritten against external contracts only + `OrderSagaTests`
  - [x] `OrderId` DERIVED by `derivedFrom`, not minted: no `UUID.randomUUID()`, no `Supplier<UUID>`
  - [x] a test proving the same event handled twice sends two identical commands
  - [x] no import of another module's internal event type
  - [x] records the shipment on `PackageWasDelivered`, not `PackageWasSent`
  - [x] skips the refund when `paymentId` is null
- [x] **2.0g** name what a payment reversal is — `DeclinePayment` + `RefundPayment`
  - [x] `DiscardPayment` names no gateway operation; a chargeback is the issuer's, never ours
  - [x] `DiscardReason` conflated "never succeeded" with "give it back"
  - [x] **defect fixed:** `Payment.discard` guarded on `Pending`, but the saga refunds a `Completed`
        payment — the refund had never happened. It threw until 2.0e, and was silent since.
        `refund()` now guards on `Completed`, pinned by a test at both the aggregate and the facade
  - [x] shipments untouched: `SendPackage` really sends, there is no reserve step to rename yet
- [x] **2.0h** two reversible holds — authorise/capture + reserve/release, both with a deadline
  - [x] payments speak the gateway's words: `AuthorizePayment`, `ConfirmPaymentAuthorization`,
        `CapturePayment`, `VoidPayment`, `RefundPayment`, `ExpirePaymentAuthorization`
  - [x] shipments reserve before they send: `ReserveStock`, `SendPackage(shipmentId)`,
        `ReleaseStock`, `ExpireStockReservation`
  - [x] `Order` joins twice — both holds confirm it, capture and delivery complete it
  - [x] `OrderPackageSent` republishes the `paymentId` the shipment never knew, so the capture
        can be sent from a stateless saga
  - [x] `OrderCancelled` carries `paymentState` and `shipmentState`: the ORDER decides between a
        void, a refund and a release, the saga only translates
  - [x] **the order is never left hanging** — both holds expire on their own, and both expiries
        reach it: `StockReservationExpired` and `PaymentFailed(AuthorizationExpired)`
  - [x] `AuthorizationExpiryWorker` and `ReservationExpiryWorker` mirror `PaymentTimeoutWorker`
  - [x] 249 tests, 0 failures, 0 skipped (was 180)
- [ ] **2.2** `ECommerceConfig` + `OrderProcessTests` happy-path transcript
  - [ ] the store is the internal channel; the integration bus is separate
  - [ ] one `confirm` drives the whole process — gateway and provider settle on their own
  - [ ] the thirteen saga subscriptions read as the process

### Defects found while expanding the hotel management tests

- [x] **`GuestStayAccountDecider` emitted `GuestCheckedIn` on a successful checkout**, not
      `GuestCheckedOut`. The account therefore never reached `CheckedOut`, and the `groupCheckoutId`
      — the only thing the group checkout saga keys on — was dropped. Fixed in the `saga` variant.
- [x] **`GuestStayAccount.evolve` threw on `GuestCheckoutFailed`.** The empty `case` block fell
      through to `case null: throw new IllegalArgumentException("Event cannot be null!")`, so
      replaying any stream that held a failed checkout blew up. Fixed in the `saga` variant.
- [ ] **The `choreography` variant has both defects, character for character.** Untouched — it has
      no tests of its own, so fixing it blind is not something I want to do unasked.

## Phase 3 — Failure paths  *(three parallel tracks, merge one at a time)*

- [ ] **3.1** out-of-stock, rejected payment, and thrown charge
- [ ] **3.2** payment timeout, including compensate-exactly-once
- [ ] **3.3** manual compensation + cancelled-before-payment (no refund)

## Phase 4 — Documentation

- [x] **4.1** README rewrite — ecommerce half rewritten for the two-hold process; the hotel
      management half left alone; three dead links to `hotelmanagement/...` fixed: two mermaid diagrams, the forwarder boundary, append-publishes, the
      two settlement seams, the stateless-saga trick, the three defences, known follow-ups

---

## Open questions

- [ ] `DeliveryProvider` mirroring `PaymentGateway` is an inference from the payments decision,
      chosen for symmetry — delivery was agreed, the seam was not discussed. Confirm at review.
- [ ] There is no "delivery failed" event: `DeliveryProviderClient` logs and leaves the shipment
      undelivered. Inventing a failure path here was judged out of scope; confirm.
- [ ] `core/messaging` duplicates `core/commands` / `core/events`, which stay because
      `hotelmanagement` depends on them. Unifying them is a follow-up, not part of this work.
- [x] Tenth defect found in step 0.4, not in spec §11: `PricedProductItem.mergeWith` added
      `productItem.quantity()` to itself instead of `quantity() + productItem.quantity()`, so adding
      the same product twice doubled the incoming quantity. **Fixed**, along with an eleventh defect
      found next to it: `ProductItems.remove` dropped the whole line regardless of quantity, even
      though `assertThatCanRemove` guards for partial removal — removing 1 of 5 removed all 5.
      `PricedProductItem.subtract` now mirrors `mergeWith`. Covered by `ProductItemsTests` (7 tests).

## Raised by Phase 2, needing Oskar's call

- [x] **Resolved on review:** the parcel no longer leaves before the money moves. `SendPackage` now
      comes off `OrderPaymentCaptured`, and `Order.recordPackageSent` acts only when the payment is
      `Captured`. A dispatch that arrives early appends nothing.
- [ ] A manual `CancelOrder` while a hold is still pending closes the order with
      `paymentState = NotAuthorized`, so nothing is voided. The authorisation that lands afterwards
      is ignored by the order and then lapses on its own through `AuthorizationExpiryWorker` — the
      deadline heals it rather than a compensation. Worth an explicit Phase 3.3 test.
- [x] **Settled:** capture first, then ship. Oskar overruled capture-at-dispatch — the realistic
      flow for this sample is that nothing goes out against an uncharged authorisation.

## Raised by Phase 1, needing Oskar's call

- [x] **Confirmed by Oskar:** `ShoppingCart.confirm()` and `cancel()` now take an
      `OffsetDateTime now` parameter instead of calling `OffsetDateTime.now()` internally, matching
      `Order` and `Shipment`. Without it the facade's injected clock is decorative and the
      happy-path assertions cannot be deterministic. `ShoppingCartEvent` and `ShoppingCartCommand`
      are untouched.
- [ ] `Order`, `Shipment` and `Payment` each gained a private no-arg constructor and `empty()`.
      Not in the plan, but `AggregateStore` and `AggregateSpecification` both need a
      `Supplier<Entity>`. Matches `ShoppingCart.empty()`.
- [x] `OrderFacade` ignored `command.cartId()`. Both the internal and the external
      `OrderInitialized` now carry it, so the order records the cart it came from.
- [x] `MessageCatcher.shouldReceiveSingleEvent` now compares recursively, matching what
      `shouldReceiveMessages` already did in the workshop original. It could not be used for any
      event carrying an array before.
- [x] `Shipment.reserveStock` with an empty product array now emits `ProductWasOutOfStock`.
      Reserving nothing is not a reservation.

## Cosmetic defects found in Phase 1, all deliberately left alone

- [x] `Payment.timeOut` threw with `discard`'s message. Fixed, then the throw itself went in 2.0e.
- [x] `Payment.discard` and `timeOut` formatted the status as `'{%s}'`. Fixed.
- [x] `ShoppingCart.addProductItem` and `removeProductItem` had their guard messages swapped. Fixed.
- [x] `OrderEvent` imported Spring's `@Nullable`, which needs JSR-305 on the classpath. Replaced
      with a plain comment. **Seven `hotelmanagement` files still import it**, so `compileJava` still
      prints `warning: unknown enum constant When.MAYBE`. Outside the agreed blast radius.
- [ ] `AggregateStore.getAndUpdate(Id, long, Consumer)` takes a primitive `long`. Nothing calls it
      yet, so there is no unboxing to NPE on; it becomes real when a caller passes an `ETag`.
