# Articles

Read on 27 November 2026 by Claude in the main session. The full texts as read are in `sources/`.

## Not read: HTTP 403

These three pages answered HTTP 403 Forbidden to a direct download and to the fetch tool. Nothing from them is in these notes. To use them, save each page as HTML or PDF into this folder.

- https://medium.com/ssense-tech/exploring-advanced-error-handling-patterns-with-event-driven-architecture-part-i-e2f37741d904
- https://medium.com/ssense-tech/exploring-advanced-error-handling-patterns-with-event-driven-architecture-part-ii-235bb72cfa19
- https://levelup.gitconnected.com/error-handling-in-event-driven-systems-1f0a7ef2cfb7

## Oskar's articles linked in the README

| Article | Main point | What the sample shows of it |
|---|---|---|
| [What texting your Ex has to do with Event-Driven Design?](https://event-driven.io/en/what_texting_ex_has_to_do_with_event_driven_design) (guide "Handling errors in event streams", 1/5) | A wrong event can't be taken back. Options, in order: think up front and test; edit before it is published (outbox delay); delete from the broker (bad idea, maybe read already); compensate, like a correcting invoice. | Compensation: `OrderCanceled` carries `paymentStatus` and `shipmentStatus`, and the saga sends `CancelPayment` or `ReleaseStock`. The event store is the outbox. Contract tests catch a changed message shape. |
| [No, it can never happen!](https://event-driven.io/en/no_it_can_never_happen/) (same guide, 2/5) | The hotel checkout that got stuck because one module never answered, and blocked the night audit. Ask the business "Okay, so how often?". Treat errors as requirements, give users a corrective action, avoid dead ends. | Deadlines and workers (`PaymentTimeoutWorker`, `AuthorizationExpiryWorker`, `UnpaidOrdersWorker`), "Retry payment", "Cancel order", failure events instead of exceptions (`CaptureGatewayClient`). |
| [Saga and Process Manager](https://event-driven.io/en/saga_process_manager_distributed_transactions) | No distributed transactions. A saga is a stateless dispatcher that sends commands built from event data only. When the event lacks data, the aggregate answers with its own event (the "trick"). Avoid a frozen workflow: try/catch → failure event, a background worker on max processing time, a manual compensation as the last resort. | `OrderSaga` has no state. `PackageWasSent` → `RecordOrderPackageSent` → `OrderPaymentDue` carries the `paymentId` → `CapturePayment`. The three workers. |
| [Internal and external events](https://event-driven.io/en/internal_external_events/) | Exposing internal events leaks abstractions: the colleague who sums `ProductItemAdded` and forgets `ProductItemRemoved` overcharges customers. Publish enriched summary events on `-external` channels. Build the external event from the state at the internal event's stream position, to avoid race conditions. Document events as API (AsyncAPI, EventCatalog, Markdown). | Forwarders, summary projections, the `summary.getVersion() >= streamPosition` guard, channels `<module>-external`, `-requests`, `-responses`, `-internal`, Springwolf and EventCatalog. |
| [Events should be as small as possible, right?](https://event-driven.io/en/events_should_be_as_small_as_possible) | "As small as possible, but not smaller." Events are public API. Granular internal events published outside create a distributed monolith. Map several internal events to one external one. | `PaymentFailed` stands for three internal events. `ShipmentFailed` for two, with the reason. External events carry only what readers use. |
| [Predictable identifiers](https://event-driven.io/en/predictable-identifiers-enabling/) | URNs let a generic module (payments) answer any requester without knowing it: the routing key comes from the URN, and the requester binds its queue with a pattern. The URN also works as an idempotency key and for webhook correlation. | `referenceId` = `urn:ecommerce:order:42-1` → routing key `ecommerce.order.42-1`, queue `orders-payments-responses` bound with `ecommerce.order.*`. Derived ids in the saga. |
| [Long-polling](https://event-driven.io/en/long_polling_and_eventual_consistency/) | Move retries from clients to the server. Blind client retries DDoS-ed a backend during a demo. Retry with exponential backoff and jitter, but always with a deadline, to avoid hanging connections and pool exhaustion. Not a default; consider push and UX first. | `LongPolling` (up to one second), `ConditionalRead` with `If-None-Match`, `304`, `503` with `Retry-After: 1`, htmx polling every second, virtual threads. |

The guide "Handling errors in event streams" on event-driven.io has five steps. The README links only the first two. Step 3 is "Fixing bugs in Event Sourcing is hard, for real?".

## "Preventing and Fixing Bad Data in Event Streams", parts 1 and 2, by Adam Bellemare (Confluent blog)

Read from <https://www.confluent.io/blog/shift-left-bad-data-in-event-streams-part-1/> and <https://www.confluent.io/blog/shift-left-bad-data-in-event-streams-part-2/>. The Medium copies of the same articles answered HTTP 403.

Part 1 starts with batch processing: there, bad data is cut out and reprocessed, a "full_refresh" in dbt's terms, and the takeaway is that this doesn't transfer to immutable streams. He also warns about side effects: decisions made on bad data, such as a car repossessed by mistake, may not be reversible, and how to undo them "is primarily a business problem. What does your business do if it makes a bad decision with bad data? Apologize? Refund?".

Strategies, in his order: **prevention** (schemas, tests, validation, "fail fast and gracefully"), **event design** (events that let you issue corrections), and **rewind, rebuild and retry** ("when all else fails").

His eight types of bad data, and the prevention he gives for each:

| # | Type | His example | His prevention |
|---|---|---|---|
| 1 | Corrupted data | faulty serializer, bytes can't be read | tests of serializers |
| 2 | Event has no schema | JSON with implicit conventions | use Avro, Protobuf or JSON Schema |
| 3 | Invalid schema | Schema Id points to no schema, or to another registry | schema registry, tests |
| 4 | Incompatible schema evolution | deserializable but not mappable to what the consumer expects | evolution rules, CI checks |
| 5 | Logically invalid value | an array in `first_name`, a null in a non-null field | strict schema |
| 6 | Logically valid, semantically wrong | Bobby Tables as a first name, an order with a negative cost | producer tests, exceptions, data quality rules (CEL in data contracts) |
| 7 | Missing events | nothing produced, or only part of it | integration tests: produce inputs, check outputs |
| 8 | Events that shouldn't have been produced | duplicates that look like new facts: a delta "add 1", an ad click, an order with its own new `order_id` shipped and billed twice | idempotent production fences out retries only; test the producer logic |

On dead-letter queues: "Approach DLQ usage with caution." A DLQ works best when "each event is completely independent" and "ordering is not important". Otherwise consumers see "an error-free, yet incomplete, stream of data".

Event design: **state events** carry the whole entity, can be compacted, and are fixed by publishing the correct state again ("fix it once, fix it right"). **Delta events** "describe a change or an action", can't be compacted, and every delta matters. To fix bad deltas there are two ways: **build forward**, adding new deltas that undo the bad ones ("very hard to accomplish in practice", because you must know each consumer's logic, and "a package, once sent, can't be unsent"), or **rewind, rebuild and retry**. He notes that "deltas are popularly used as the basis of event sourcing" and recommends deltas between services only "if you own both the producer and the consumer". **Hybrid events** carry the delta and the new state (`debitAmount: 100, newTotal: 300`), with the guarantees of state events.

Rewind, rebuild and retry: rebuild from an external source of truth (a log file) or, when the topic is the only source, reprocess the bad topic into a new good topic, then move each consumer to it. That needs consumer snapshots aligned with input offsets, or a rebuild from the start, which works only if every input keeps its full history. "Doing it once will make you never want to do it again."

### How the sample relates to his points

- Type 1 and 3: an unreadable message, or a class outside the module's trusted packages (`java.lang.ProcessBuilder` in the README), is dead-lettered, never retried (`MessageFailureStrategy.retryUnlessUnreadable`).
- Type 2 and 4: the sample uses JSON with the type name in `__TypeId__`. No schema registry. The contract is kept by Strictland snapshots in the build, by the AsyncAPI documents and by `MessageMapping.register(oldName, ...)` transformations for old shapes.
- Type 6: `ShoppingCart` throws for a broken rule over HTTP; orders, payments and shipments ignore a command they can't use.
- Type 8: the saga derives the order id from the cart id, and the payment and shipment ids from the order id. So a repeated checkout event can't create a second order with a new id: the predictable id turns his worst duplicate into a no-op.
- DLQ caution and ordering: the Kafka bus dead-letters to `<queue>-dlt` per queue. A message replayed from there after the order moved on meets an idempotent decider, which ignores it.
- Deltas vs state: inside each module the events are deltas (Event Sourcing). Between modules, forwarders publish summary events built from a summary projection: closer to his state or hybrid events. The internal and external events articles argue the same split.
- Rewind and rebuild: the event store is the full history, so a view is rebuilt by resetting its checkpoint in `processor_checkpoints`. The README's limitation "A view can come back after a replay" is a real example of a rebuild going wrong.
