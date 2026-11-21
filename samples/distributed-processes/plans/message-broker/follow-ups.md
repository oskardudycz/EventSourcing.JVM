# Follow-ups after the message-broker plan

## F1. Naive stream subscription left in the KurrentDB buses (done, option A, in step 2 of the alignment-and-cleanup plan)

Oskar asked why a subscription goes to a stream and not to `$all`, and why it isn't like the main samples (2026-11-20).

What is there now:

- `core/kurrentdb/subscriptions/KurrentDBSubscription.subscribeToStream` keeps the comment "Note this is a pretty naive version of subscription handling. It doesn't have error handling, retries and resubscribes." Step 6 removed its `subscribeToAll` variant, but kept the stream variant.
- Only `core/commands/KurrentDBCommandBus` (stream `_commands-all`) and `core/events/KurrentDBEventBus` (stream `_events-all`) call it. They use one stream as a bus.
- No code in `src/main` or `src/test` creates either bus. The hotel management examples use only the `CommandBus` and `EventBus` interfaces.
- The e-commerce part already subscribes to `$all` through `KurrentDBEventStore.subscribe` and `AllStreamSubscription`, which resubscribes from the stored checkpoint with back-off.

Options:

- A (recommended): delete `KurrentDBSubscription`, `KurrentDBCommandBus` and `KurrentDBEventBus`, because nothing runs them.
- B: if the hotel management examples should run on KurrentDB, wire them to `KurrentDBEventStore.subscribe` (`$all`, filtered by event type, with checkpoints and retries) and add tests.

The same naive class also exists in `samples/uniqueness`, which is outside this plan.

## F2. `continuing` re-parents commands that already have the purchase context (decision)

Spec, section on tracing: "A handler that handles a command with no trace context from the purchase (a webhook command, an answer from the fulfillment service) continues the trace from the last event of the stream it changes". The code applies `continuing` to every command in the plan's list, without that condition, so a command that `OrderSaga` publishes inside a purchase step is also re-parented to the last event of its stream (step 12 review, Step-6).

The condition is not simple to check. A webhook command also arrives with a `correlationId`, because the worker generates a new one for it (spec: "A worker or a webhook that publishes outside a purchase generates a new `correlationId`"). So the handler can't tell the two cases apart from the presence of a context.

Options:

- A (recommended): change the spec to match the code: every listed command continues from the last event of its stream. The purchase tests (36.1, 37.1) pass with this, and both cases keep the purchase's `correlationId` on the new events.
- B: mark the commands sent from outside a purchase (webhook, fulfillment answers) and apply `continuing` only to them. It needs a new header or command flag and tests for both cases.

## F3. Test gaps left open by the reviews

These are small, and each one makes an existing check stricter. None of them changes behavior.

- Step 12, Step-3: no test for `getAndUpdateContinuingTrace` on a stream with no events (the handler then runs with only the consumer's span).
- Step 13, Step-1: on RabbitMQ, 37.1 checks `causationId` only as not blank. A strict check needs a queue bound to every exchange before checkout to read the commands' message ids.
- Step 14, Step-2: 38.1 doesn't count step spans, so a lost step span would pass.
- Step 14, Step-4: 42.1 checks that nothing reaches a missing OTLP endpoint, but not that the exporters ran.

Recommendation: one small follow-up step that adds the four checks.

## Choices the coordinator accepted during the run (Q30)

They are recorded in the review files with the reason. Listed here so you can reverse any of them:

- Settings match exact values; `Kafka` or a blank value stops the start and names the allowed values (step 2).
- A new projection document costs about 3 statements (merge selects first); worker tests filter events by `toString().contains(paymentId)` (step 3).
- `application.properties` got settings the plan didn't name (H2 dialect, open-in-view off, column names), each forced by the step (step 3).
- Each retry of a handler gets its own step span (step 12, Step-4).
- 36.1 checks that each `causationId` is a known message of the purchase, not the exact parent per step (step 12, Step-1).
- The stored `$spanId` is the step span, not the `streams.append` span (step 14, Step-6).
- `ShoppingCartsController.checkOut` and `ShopPages.checkOut` decide from the cart's stream (`ShoppingCartFacade.isCheckedOut`), not from the async cart view, so checkout no longer returns 409 on a slow projection (step 12, Standards-1).
- The Kafka dead-letter test checks at least 3 attempts inside 1.5 s, not exactly 3 (step 13).
- Every Spring test class that starts the application on a shared broker or event store has `@DirtiesContext`, and each such context gets its own PostgreSQL database (`TestDatabases.newPostgreSQLDatabase()`), so cached contexts don't consume each other's messages or checkpoints (steps 13 and 14).
- `spring-boot-docker-compose` is also a `testImplementation` dependency, so the `in-memory` profile test can fail (step 15, Step-2).
