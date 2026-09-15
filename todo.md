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
- [ ] **A.1** `ShoppingCartFacade` (command records, no publishing) + `ShoppingCartsConfig` + tests
- [ ] **A.2** cart forwarder on the integration bus + tests *(the only forwarder that re-reads)*

### Track B — orders
- [ ] **B.1** defects + `OrderTests`
  - [ ] `cancel` guard inverted — an opened order can never be cancelled
  - [ ] `when(OrderInitialized)` never sets `totalPrice`
  - [ ] PascalCase components in `OrderCommand` / `OrderCancelled`
  - [ ] `InitializeOrder` uses the wrong `PricedProductItem`; add `orderId` + `cartId`
  - [ ] `mapToStreamId`; `OrderCancellationReason` gains `PaymentFailed`, `Requested`
- [ ] **B.2** `OrderFacade` (replaces the empty `OrderService`) + `OrdersConfig` + tests
- [ ] **B.3** `OrderExternalEvent` + forwarder, wired + tests

### Track C — payments
- [ ] **C.1** `mapToStreamId` + `PaymentTests` (including the settle-exactly-once guards)
- [ ] **C.2** `PaymentFacade` (replaces the empty `PaymentService`) + `PaymentsConfig` + tests
- [ ] **C.3** `PaymentGateway` + `PaymentGatewayClient` + four test doubles
  - [ ] auto-completing, auto-rejecting, throwing, silent
  - [ ] a thrown charge becomes `PaymentDiscarded`, never a propagated exception
- [ ] **C.4** payment forwarder on the integration bus + tests
- [ ] **C.5** `PendingPayments` + `PaymentTimeoutWorker.run(now)` + tests

### Track D — shipments
- [ ] **D.1** defects + delivery + `ShipmentTests`
  - [ ] delete the copy-pasted `shipments/PaymentService.java`
  - [ ] camelCase `ShipmentCommand`; `SendPackage` gains `shipmentId`
  - [ ] static factory, `mapToStreamId`, `when` sets `orderId`
  - [ ] `PackageWasDelivered` + `deliver(now)` with guards
- [ ] **D.2** `ShipmentFacade` (both commands) + `ShipmentsConfig` + tests
- [ ] **D.3** `DeliveryProvider` + `DeliveryProviderClient` + two test doubles
- [ ] **D.4** `ShipmentExternalEvent` + forwarder, wired + tests

## Phase 2 — Wiring  *(sequential — needs all of Phase 1)*

- [ ] **2.1** `OrderSaga` rewritten against external contracts only + `OrderSagaTests`
  - [ ] injected `Supplier<UUID>`; no `UUID.randomUUID()` inside
  - [ ] no import of another module's internal event type
  - [ ] completes on `PackageWasDelivered`, not `PackageWasSent`
  - [ ] skips the refund when `paymentId` is null **or** the reason is `PaymentFailed`
- [ ] **2.2** `ECommerceConfig` + `OrderProcessTests` happy-path transcript
  - [ ] the store is the internal channel; the integration bus is separate
  - [ ] one `confirm` drives the whole process — gateway and provider settle on their own
  - [ ] the eight saga subscriptions read as the process

## Phase 3 — Failure paths  *(three parallel tracks, merge one at a time)*

- [ ] **3.1** out-of-stock, rejected payment, and thrown charge
- [ ] **3.2** payment timeout, including compensate-exactly-once
- [ ] **3.3** manual compensation + cancelled-before-payment (no refund)

## Phase 4 — Documentation

- [ ] **4.1** README rewrite: two mermaid diagrams, the forwarder boundary, append-publishes, the
      two settlement seams, the stateless-saga trick, the three defences, known follow-ups

---

## Open questions

- [ ] `DeliveryProvider` mirroring `PaymentGateway` is an inference from the payments decision,
      chosen for symmetry — delivery was agreed, the seam was not discussed. Confirm at review.
- [ ] There is no "delivery failed" event: `DeliveryProviderClient` logs and leaves the shipment
      undelivered. Inventing a failure path here was judged out of scope; confirm.
- [ ] `core/messaging` duplicates `core/commands` / `core/events`, which stay because
      `hotelmanagement` depends on them. Unifying them is a follow-up, not part of this work.
- [ ] Tenth defect found in step 0.4, not in spec §11: `PricedProductItem.mergeWith` adds
      `productItem.quantity()` to itself instead of `quantity() + productItem.quantity()`, so adding
      the same product twice doubles the incoming quantity rather than summing. Owned by track A.
