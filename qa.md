# Brainstorm Q&A — Making the ECommerce distributed-processes sample realistic

Target: `samples/distributed-processes/src/main/java/io/eventdriven/distributedprocesses/ecommerce`

## Starting point (established before Q1)

- `OrderSaga` has eight `on(event)` methods scheduling commands on `CommandBus`, but nothing
  constructs it and nothing subscribes it. No composition root, no `EventStoreDBClient` bean.
- `OrderService` and `PaymentService` are empty class bodies; `shipments` contains a
  copy-pasted `PaymentService` too. So the commands the saga schedules have no handlers.
- `ShoppingCartExternalEventForwarder` / `PaymentExternalEventForwarder` have no callers.
- Net effect: the cart -> order -> payment -> shipment chain is inert at runtime.
- By contrast `workshops/event-driven-architecture/solved/.../e03_businessprocesses/sagas`
  already has the desired shape: pure static saga functions returning `SagaResult`
  (`Send` / `Ignore`), facades doing decide -> store -> publish, `*Config` classes as the
  composition root wiring `eventBus.subscribe(...)` -> saga -> `commandBus.send(...)`, and
  tests asserting the full interleaved command+event transcript via `MessageCatcher`.

---

## Q1. What should the ecommerce sample actually become — what's the destination shape?

**Answer: Port the workshop shape onto ecommerce.**

Bring facades + `*Config` composition root + `SagaResult` + `MessageCatcher`-style tests from
`e03_businessprocesses` into the ecommerce sample, keeping in-memory synchronous buses as the
primary runtime. ESDB / persistence becomes a later, optional layer.

---

## Q2. How should module boundaries and message propagation be modelled?

**Answer: Complete the internal/external split with forwarders.**

Each module keeps two event sets: internal domain events, plus external integration events
produced by a wired forwarder that re-reads the entity to enrich. Example:
`ShoppingCartConfirmed` (internal) -> forwarder reads the cart -> `ShoppingCartFinalized`
(external, carrying product items and total price) -> `OrderSaga.handle(...)` -> `Send(InitializeOrder)`.
Chosen deliberately over the lighter options because it makes the translation step explicit,
which is the realistic part currently missing.

---

## Q3. What channels should carry messages?

**Answer: three roles (per-module internal event bus, shared integration event bus, command bus)
— but as abstractions, not necessarily three implementations.**

Oskar: "I'd vote for 1 but it depends how will you implement it. Bus technically can be the same
in those samples, in reality also; using message bus as an abstraction helps in that. In the
workshop I show that a command bus can be e.g. RestTemplate or a RabbitMQ queue or a Kafka topic,
depending on how we configure that."

Consequence for the spec: the three roles are distinct interfaces so intent is visible in the
code, while the composition root is free to back all three with a single in-memory bus in the
sample, and a per-role transport (HTTP / queue / topic) in a realistic deployment.

---

## Q4. How should the saga be shaped and invoked?

**Answer: the saga keeps the `CommandBus` it has today.**

Oskar: "It should have still command bus as it has."

So `OrderSaga` stays an instance with a constructor-injected command bus and `on(event)` methods
that send commands directly. The pure `handle(event) -> SagaResult` style from the workshop is
rejected for this sample. The open sub-question is how those `on(...)` methods get subscribed
(see Q5).

---

## Q5. Where does the subscription wiring live?

**Answer: explicit subscriptions in a Config class.**

A composition-root Config lists every step, e.g.
`eventBus.subscribe(ShoppingCartFinalized.class, saga::on)` — one line per happy-path step plus
the compensation steps. Compile-time checked, greppable, and the whole process is readable as a
list. Rejected: saga self-registration, and Spring `@EventListener` reflection-based routing.

---

## Q6. What should the write side of each module look like?

**Answer: facade over the existing mutable aggregates.**

Rename `*Service` -> `*Facade`, give each method a command-record parameter so
`commandBus.handle(InitializeOrder.class, facade::initializeOrder)` type-checks (today
`ShoppingCartService` takes unpacked arguments, which is why the command records are dead code),
and fill in the empty `OrderService` / `PaymentService` and the missing shipment handler.
All existing `AbstractAggregate` files stay. Smallest diff; no decider rewrite.

---

## Q7. How much of the failure surface should the sample implement?

**Answer: all three defences from the article, including manual compensation.**

1. Failure events — handlers wrap work in try/catch and emit a `*Failed` event instead of
   throwing, so failure is a first-class business outcome.
2. Timeout worker — a background worker emits `PaymentTimedOut` when a payment stays pending too
   long (the `Payment` aggregate already has `timeOut` / `PaymentTimedOut`), feeding the same
   cancellation path. Tests will need a controllable clock.
3. Manual compensation — an explicit operator-triggered entry point that cancels a stuck order
   and refunds.

Plus the full happy path and both compensation branches:
`ProductWasOutOfStock -> CancelOrder`, `PaymentFailed -> CancelOrder`, `OrderCancelled -> DiscardPayment`.

---

## Q8. Should the sample gain an HTTP API?

**Answer: no HTTP — facades are the entry point.**

Tests (and optionally a small runner) call facades directly. No `spring-boot-starter-web`, no
controllers, no read models. The manual compensation is simply another command sent on the
command bus rather than an endpoint. Keeps the reader's attention on the process instead of REST
plumbing.

---

## Q9. What do the three test levels mean here?

**Answer: all three in-memory; EventStoreDB stays out of the process tests.**

- Unit — aggregate behaviour via `EventSourcedSpecification` (Given/When/Then).
- Integration — one facade plus its buses per module: command in, stored state and published
  events out.
- E2E — the whole process through the composition root, asserting the full interleaved
  command+event transcript with a `MessageCatcher`-style spy.

Fast, deterministic, no infrastructure. The ESDB-backed variants are explicitly not part of this
work.

---

## Q10. What persists the aggregates in the in-memory process tests?

**Answer: an in-memory fake behind the existing `core/esdb/EventStore` wrapper.**

`EventStore` becomes an interface (`read`, `append` with expected revision, returning
`Success | Conflict | StreamAlreadyExists | UnexpectedFailure`), with the current ESDB class as
one implementation and a new in-memory one. `AggregateStore` and the facades stay untouched, one
fake covers every store built on the contract, and optimistic-concurrency behaviour is still
exercised in tests.

---

## Q11. Should messages travel in envelopes with metadata?

**Answer: bare messages — no envelope in the sample.**

Buses carry plain command and event records, as the workshop's in-memory version does.
Correlation lives in the domain itself (orderId, paymentId, cartId on the events). Keeps handlers
and the transcript assertions readable. The unused `EventEnvelope` / `CommandEnvelope` /
`*Metadata` classes are not adopted here.

---

## Q12. How wide should the blast radius be?

**Answer: fix only what the order process needs.**

In scope: the `ecommerce` package, plus the `core` changes the process requires (`EventStore`
extracted to an interface with an in-memory implementation, in-memory event/command buses), plus
these defects, each of which blocks the process:

1. `Order.cancel` guard is inverted (`Order.java:75-76`) — throws when status is `Opened`, so a
   freshly opened order can never be cancelled, breaking compensation.
2. `shipments/PaymentService.java` — copy-pasted empty class in the wrong module.
3. `AggregateStore.get` never sets `version` from the stream, so the no-revision `getAndUpdate`
   always sees `-1` and optimistic concurrency does not work.
4. `InitializeOrder` carries `shoppingcarts.productitems.PricedProductItem` while `OrderEvent`
   uses `orders.products.PricedProductItem`, with no mapping anywhere.
5. `OrderCommand` record components are PascalCase (`UUID OrderId`), unlike every other module.

Out of scope: the `hotelmanagement` package (all three variants), and the unused `core` classes
(`CommandHandler`, `SyncProcessor`, `HandlerWithAck`, `RetryPolicy`, `AbstractProcessManager`,
the envelopes) — left exactly as they are.

---

## Q13. How should the composition root be split?

**Answer: per-module Config plus one `ECommerceConfig` composing them.**

Each module's Config registers the commands that module accepts and its own
internal-to-external forwarder, so a module stays self-contained and could be deployed alone.
`ECommerceConfig` builds the buses, calls the four module Configs, and owns the `OrderSaga`
subscriptions — the saga being the only cross-module citizen.

---

## Q14. Who completes or fails a payment?

**Answer: implement the payments module properly — "basic feature and logic".**

Oskar: "We should implement the payment module with basic feature and logic."

So payments becomes a real module rather than a test puppet or an instant-complete shortcut:
the facade handles `RequestPayment`, `CompletePayment`, `DiscardPayment` and `TimeOutPayment`,
the aggregate keeps its `Pending -> Completed | Failed` transitions, and a payment genuinely sits
in `Pending` between request and settlement — which is what makes the timeout defence meaningful.

Assumption to confirm at review: the actual card charge stays behind a minimal injectable
abstraction (mirroring `Shipment`'s existing `Function<ProductItem, Boolean> isProductAvailable`),
so a test can pick success, failure, or no response at all.

---

## Q15. How should time be handled?

**Answer: injected clock plus a worker with an explicit `run(now)`.**

A `Supplier<OffsetDateTime>` (or `Clock`) is injected wherever time is read, and
`PaymentTimeoutWorker.run(now)` scans pending payments and sends `TimeOutPayment` for those older
than the configured timeout. Production would call it on a schedule; tests call it directly with a
chosen instant. No sleeps, no Awaitility, no flakiness.

---

## Q16. What documentation should ship with it?

**Answer: rewrite the README with a diagram, but keep it tight.**

Oskar: "1 but don't go wild with details of implementation and .net ecommerce sample link is not
needed."

So: the process steps and who owns each, a mermaid sequence diagram of the happy path and the
compensation branches, the internal-to-external forwarder boundary, where the saga is wired, and
the three failure defences. Link the article. No file-by-file implementation walkthrough, no link
to the .NET ECommerce sample.

---

## Correction found while writing the spec

Q10's chosen option assumed `AggregateStore` sits on top of the `core/esdb/EventStore` wrapper.
It does not — `AggregateStore` takes an `EventStoreDBClient` directly
(`core/aggregates/AggregateStore.java:16-28`). So "AggregateStore untouched" is not achievable:
it has to be re-pointed at the `EventStore` abstraction. The spec keeps an
`EventStoreDBClient`-taking constructor that wraps the client in `ESDBEventStore`, so
`hotelmanagement` and the existing tests keep compiling.

---

## Q17. New GitHub repo, and should the spec be committed?

**Answer: no repo, no commit.** `spec.md` and `qa.md` stay untracked in `EventSourcing.JVM`;
Oskar handles Git himself.

---

# Round 2 — questions raised while writing plan.md

## Q17. Who drives a payment from Pending to settled?

**Answer:** "We should have some interface for payment gateway that can be replaced in tests with
some mock to e.g. automatically complete or reject."

So `PaymentGateway` is the seam. A `PaymentGatewayClient` inside the payments module reacts to the
internal `PaymentRequested` and calls it; the gateway implementation decides what comes back. Test
doubles: one that auto-completes (sends `CompletePayment`), one that auto-rejects (sends
`DiscardPayment`), one that stays silent so the timeout worker has something to catch. The process
then runs end to end without a test reaching into its middle.

## Q18. Should the saga refund a payment that has already failed?

**Answer: guard on the cancellation reason in the saga.**

`on(OrderCancelled)` skips `DiscardPayment` when the reason is `PaymentFailed` — the payment is
already dead and the aggregate would throw. The saga stays stateless and the rule is one visible
line.

## Q19. What happens to `DeliverPackage`?

**Answer: implement delivery too.**

`DeliverPackage` gets handled, `PackageWasDelivered` is added, and the process gains a step: a
*delivered* package completes the order, not a sent one.

## Q20. Fold the code-level corrections into spec.md?

**Answer: patch spec.md so both files agree.**

## Q21. Should the in-memory event store publish on append?

**Answer: yes — append stores and dispatches, following the introduction-to-event-sourcing
workshop.**

Oskar: "just check how in memory event store works in introduction to event sourcing workshop."

`workshops/introduction-to-event-sourcing/solved/.../e13_entities_definition/core/EventStore.java`
is both the store and the publishing mechanism: `appendToStream(streamId, events)` persists the
events and fans them out to typed subscribers through middleware, in one call. Adopting it removes
the store-then-publish dual write, and facades stop publishing entirely — they just append.

Consequence: the module-internal event bus is no longer a separate object. The three channel roles
from Q3 survive as abstractions (`InternalEventBus` becomes a subscription-only interface that
`InMemoryEventStore` implements), which is consistent with Q3's "bus technically can be the same".

## Q22. Should the in-memory store model optimistic concurrency?

**Answer: keep it — expected revision, conflicts, ETags.**

The intro-workshop stores have none, but every `ShoppingCartCommand` already carries an
`expectedVersion` and `AggregateStore` already returns `ETag`s. Dropping concurrency would mean
stripping all of that; keeping it means the `AggregateStore.get` version bug must be fixed.

## Q23. Should the in-memory store round-trip events through JSON?

**Answer: yes — serialize to JSON envelopes, as e06 and e13 do.**

Stores `(eventType, json)` pairs and deserializes on read. Proves every event is serializable,
deep-copies so no test can mutate stored state, and exercises the same Jackson configuration
production would.
