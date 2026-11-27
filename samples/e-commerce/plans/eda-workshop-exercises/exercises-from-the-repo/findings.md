# Exercises from what the repo already shows

Written on 27 November 2026 by Claude in the main session. Paths are relative to `samples/e-commerce`. Every code claim was checked in the code on that date. Ideas are options for discussion, not decisions.

## Facts the scenario switch can build on

- All messages between modules go through `MessageBus.publish` (`src/main/java/io/eventdriven/core/messaging/MessageBus.java`). `use(middleware)` sees every published message, with its ids, before the queues. Middleware can observe or throw, but it can't drop, delay or duplicate a message. A decorator around `MessageBus` can do all of these.
- The bus is chosen by `messaging.bus` and the store by `eventstore.type` in `ECommerceApplicationConfig`. A scenario switch could be one more property in the same style, for example `./gradlew bootRun --args='--workshop.scenario=...'`.
- Message ids published while handling a message are derived: `UUID.nameUUIDFromBytes(handledMessageId:typeName:position)` (`core/messaging/MessageContext.java`). A message started by an HTTP request gets a random id. So a fault keyed on the message id is repeatable only within one run.
- Business ids repeat on every machine: the login makes the customer id from the name, the order id is `{customerId}-{n}`, and the payment and shipment ids are derived from the order id (README, "The order decides, the saga only translates"). A fault keyed on a message type plus a business id, such as "the first `PaymentAuthorized` of the customer `Anna`", gives every participant the same incident. Chaos Monkey's `deterministic=true` counts calls ("every Nth"), so it can't do this (see `../tools-and-versions/findings.md`).
- The failure decision already exists as a seam: `MessageFailureStrategy` returns `RETRY` or `DEAD_LETTER`, and the default dead-letters only unreadable messages (`core/messaging/MessageFailureStrategy.java`). On Kafka, failures go to `<queue>-dlt` and count `messaging.dead_lettered` (`core/messaging/kafka/KafkaMessageBus.java`).
- Kafka topics have 3 partitions, and the routing key is the record key (`KafkaMessageBus.java`, `PARTITIONS = 3`). Ordering holds per key, not per topic.
- Store subscriptions (projections, forwarders, saga) have no dead-letter queue: a failing handler keeps its checkpoint and subscribes again after a back-off (README, "Delivering messages reliably"). A poison event there blocks that subscription and everything behind it.

## What participants can already do with no change

| Exercise | Steps | What it teaches | Where they see it |
|---|---|---|---|
| Happy path, then follow it | HOWTO steps 2 to 6, then find the purchase in Tempo by `{ span.eventdriven.stream.name = "Order-<id>" }` | The lasagne of command/event, two correlation ids per purchase (Check out and Pay now) | Shop pages, Tempo, **Purchase flow** dashboard |
| Out of stock → compensation | Buy "Limited edition trail shoes" (out of stock from the start), or mark a product out of stock on `/sandbox/warehouse` | `ShipmentFailed`, the order waits for the payment's answer, then `OrderCanceled` → `CancelPayment` only if authorized | Order page shows Canceled, trace shows `ReleaseStock` absent and `CancelPayment` present or absent |
| Rejected fulfillment → compensation after confirmation | Pay, then **Reject** on `/sandbox/warehouse` | Compensation of both reservations, the saga only reads `paymentStatus` and `shipmentStatus` | Trace, `OrderCanceled` payload in KurrentDB UI |
| Declined card, pay again | Card 4000 0000 0000 0002 on the sandbox page | A decline is not a failure event; the customer retries on the same page | Sandbox page, no new events |
| Duplicate command, idempotency | Springwolf UI **Publish** the same `RecordOrderPaymentAuthorization` twice, or a late `PaymentFailed` for an old `paymentId` | Deciders return no events; nothing appended; `command.handle` span shows no new version | Tempo span attributes, **Commands and event store** |
| Read your own writes | In Swagger, check out, then `GET` the order with and without `If-None-Match` | Long polling up to one second, `304`, `503` + `Retry-After` | Swagger responses, `http.server.requests` |
| Topology from documentation | Find in EventCatalog who reads `PaymentFailed`, which queue binds `payments-responses` and with which pattern | Internal vs external events, URN routing keys, requests/responses channels | EventCatalog, Springwolf, Kafka topics list |
| Contract break | Rename a field in an external event and run `./gradlew test --tests '*ContractTests'` | Strictland snapshot fails and writes `.snap.received.json`; old stored events still need reading | Test output (needs a JDK; demo by the trainer for others) |

## Gaps that block some exercises

Decided in Q4 (`../qa.md`): the sandboxes simulate the outside failures, the deadlines become settable for the workshop, and each scenario sets up its own example data, with some exercises done by hand in the app.

- **The capture never fails in the running app.** `SandboxPaymentGateway.capture` always publishes `ConfirmPaymentCapture`. Only the tests refuse a capture (`PaymentProvider.refusesTheFirstCaptureOf`). So the **Capture failed** and **Money lost** alerts can't fire in a live session today.
- **Deadlines are long and hard-coded.** `Payment.Validity` is 24 hours (`PaymentsConfig`), `PaymentTerms` 7 days (`OrdersConfig`), the sandbox hold 7 days (`SandboxPaymentGateway.holdValidity`). The workers run every minute. A timeout or expiry exercise needs these as properties, set to minutes in a workshop profile.
- **No traffic.** Nothing runs purchases on its own. Rate-based alerts (Payments failing: at least 5 payments in 15 minutes; Commands failing; Views falling behind) need a steady flow. A shopper that buys as fixed customer names (`Anna`, `Ben`, ...) in a loop keeps ids repeatable.
- **Prices are random per run** (`RandomProductPriceCalculator`, `ThreadLocalRandom`), so `ProductItemPriceChanged` never happens live and totals differ between participants.
- **One process.** The four modules run in one Spring Boot app named `e-commerce`, so a Tempo service graph shows one service. The topology has to come from EventCatalog, the channels and the span attributes (`messaging.destination`, `eventdriven.processor.name`). Not yet checked whether Tempo's service graph can use those.

## Scenario ideas for the switch, by workshop topic

Each scenario: what is injected (at the bus or store decorator, or in a sandbox), what participants see first (the symptom given in the incident ticket), and what the root cause teaches. All keyed on message type plus business id, so they repeat on every machine.

| Topic (workshop Day 3/4) | Injected | Symptom in the ticket | Teaches |
|---|---|---|---|
| Delivery guarantees, idempotency | Publish `PaymentAuthorized` twice for some orders | "Nothing" — the shop works. Task: prove that duplicates happened and were absorbed | At-least-once, idempotent deciders, how to see a no-op in traces and metrics |
| Idempotency bug | A switched-on bug in one projection or decider that doesn't skip a repeated event, plus duplicates | "Revenue on the dashboard is higher than the payments" | Why the version check in `Projection` matters; compare metrics with the event store |
| Lost message | Drop `StockReserved` for one customer after the forwarder | "Anna's order stays in Processing" | No timeout covers a missing reservation before payment; where the trace ends; what a worker on max processing time would fix (Saga article) |
| Poison message on the bus | Throw in a handler for one message type until dead-lettered | "Dead letters alert fired" | Retry vs dead-letter (`MessageFailureStrategy`), finding the message in `<queue>-dlt`, replaying it |
| Poison event in a store subscription | Throw in a projection for one event | "Order pages show old status, Views falling behind" | No DLQ on subscriptions; one bad event blocks the rest; `eventdriven.subscription.lag` |
| Slow view, eventual consistency | Delay a projection | "API answers 503 with Retry-After" | Long polling limits, the one-second deadline, why not blind client retries |
| Ordering | Delay or reorder messages for one key, or route a key wrongly | "Order completed but payment shows authorized" or similar | Ordering per key/partition; the order accepts reservations in either order |
| Webhook never arrives | Sandbox drops `ConfirmPaymentCapture` or `ConfirmPackageSent` | "Package sent, customer never charged" | Deadlines, `AuthorizationExpiryWorker`, `UnpaidOrdersWorker`, "No, it can never happen!" |
| Provider refuses the capture | Sandbox sends `FailPaymentCapture` | "Capture failed alert" | Failure events instead of exceptions; retry payment after the package left |
| Trace propagation | Strip `traceparent` from broker headers for one channel | "The trace stops at payments" | Trace context vs correlation ids; `expectCorrelatedTrace` vs `expectCausation` |
| Contract change | Publish an old or renamed shape of an external event | "Orders ignore payments since the deploy" / dead letters with `UnreadableMessageException` | Versioning with `MessageMapping.register(oldName, ...)`, contract tests |

## Exercises from Bellemare's bad-data types and fixes

See `../articles/findings.md`. Each one uses something the sample already has.

| His point | Exercise in the sample | What participants find |
|---|---|---|
| Type 8, a duplicate that looks like a new fact | Publish `ShoppingCartCheckedOut` twice for one cart (Springwolf **Publish**), or let the scenario duplicate it | Still one order: the saga derives the order id from the cart id. Then discuss what would happen with a random order id: two orders, shipped and billed twice |
| Types 1 and 3, unreadable data | Publish a message with an unknown or untrusted type name | Dead-lettered at once, not retried; found in `<queue>-dlt` and on the **Dead letters** panel |
| Type 4, incompatible evolution | Publish an old shape of an external event after a rename | Fails or dead-letters until `MessageMapping.register(oldName, ...)` maps it; the contract test shows the change in the build |
| Type 7, missing events | The scenario drops one forwarder's message | Order stuck; which deadline catches it, and which step has none |
| DLQ caution, ordering | Dead-letter `StockReserved` for one order, let later messages pass, then replay it from the DLQ | Whether the order ends right after the replay; the idempotent decider decides, not the DLQ |
| Build forward vs rewind and rebuild | A projection bug (switched on by the scenario) writes a wrong status, then is switched off | Two fixes: correct forward with new events (compensation, his "very hard") or rebuild the view from the event store by resetting its checkpoint; the README limitation "A view can come back after a replay" |
| Delta vs state events | Compare an internal event stream in KurrentDB with the external summary event on Kafka | Event Sourcing keeps deltas inside; forwarders publish summary events, close to his state or hybrid events. Discussion: what each consumer needs to fix bad data |

## LLM and MCP use, tied to the exercises

- Grafana MCP (`grafana/mcp-grafana` v2.4.0): `search_tempo_traces`, PromQL, LogQL, alert rules. `--disable-write` keeps it read-only. It needs `GRAFANA_URL` and a token or user/password; the README doesn't cover anonymous access. Use: "Why did the Dead letters alert fire?" with the trace and metrics.
- KurrentDB MCP (`kurrent-io/mcp-server`): read streams and projections. Use: "Show the events of `Order-anna-1` and tell me what happened."
- EventCatalog: the built-in MCP server needs SSR mode and a Scale licence; the standalone server is archived. Without a licence, the generated AsyncAPI documents (`eventcatalog/specs/*.json`) or the static site can go to the LLM as files.
- Spring Boot MCP: not researched yet.
- An exercise in itself: let the LLM name a root cause, then check it against the trace and the events. The workshop page says GenAI output needs checking.

## Still to research

- Jib and a plain Dockerfile, next to `bootBuildImage` (see `../running-without-java/findings.md`).
- Whether the Grafana MCP server works against otel-lgtm without a token.
- Whether Confluent's MCP server works with a local broker.
- The three articles that answered HTTP 403 (see `../articles/findings.md`).
