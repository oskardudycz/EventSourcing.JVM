# Acceptance criteria of the exercises

Draft after Q14 in `../qa.md`. Exercises and order from `outline.md`. Paths are relative to `samples/e-commerce`; code paths are under `src/main/java/io/eventdriven/`.

Each criterion is Given / When / Then:

- **Given**: the setup and the conditions before the exercise.
- **When**: what is run, and what fault the scenario injects.
- **Then**: what goes wrong, how attendees diagnose it, and how it is fixed. "Fixed in the sample" means the sample already handles it and the attendee finds out how. "To fix" means the attendee or the trainer has to change something.

Marked **[new]**: things the sample doesn't have yet and has to get (see "What the sample needs" at the end).

## Common setup

- Start: `docker compose --profile workshop up` in `samples/e-commerce` **[new]**. Scenario: `WORKSHOP_SCENARIO=<exercise> docker compose --profile workshop up -d app` **[new]**. With a JDK: `./gradlew workshop -Pscenario=<exercise>` **[new]**.
- URLs: shop <http://localhost:8080/shop>, Swagger <http://localhost:8080/swagger-ui/index.html>, Springwolf <http://localhost:8080/springwolf/asyncapi-ui.html>, sandbox warehouse <http://localhost:8080/sandbox/warehouse>, Grafana <http://localhost:3000>, EventCatalog **[new as a container]**, Kafka UI **[new]**, pgAdmin (Compose service `pgadmin`, port 5050). The workshop profile uses the PostgreSQL event store (Q23): events are rows of the `messages` table, with `message_metadata` holding `correlationId`, `causationId`, `traceId` and `spanId` (`core/eventstore/postgresql/PostgreSQLEventStore.java`, `metadataOf`). KurrentDB and its UI are an option.
- The shopper **[new]** buys as fixed customers. The login makes the customer id from the name, so the ids are the same on every machine. Each fault of a scenario hits one named customer; the others shop normally.
- Stream names: `ShoppingCart-<id>`, `Order-<id>`, `Payment-<id>`, `Shipment-<id>` (the facades' `mapToStreamId`). Subscription checkpoints: table `processor_checkpoints` (`processor_id`, `last_processed_position`) in PostgreSQL.
- In every exercise, attendees may ask an LLM with the MCP servers, and must check its answer against a trace, an event or a document.

---

## 1. `tools`

### AC 1.1: Find one purchase in every tool

- **Given** the workshop profile runs with no scenario, and the shopper runs.
- **When** the attendee logs in under their own name, adds two products, checks out, pays with 4242 4242 4242 4242 and clicks **Send** on the sandbox warehouse.
- **Then** the order page shows **Completed** with "Sandbox Express". The attendee finds the same purchase:
  - in Tempo, by `{ span.eventdriven.stream.name = "Order-<order id>" }`;
  - in pgAdmin, as the rows of the streams `ShoppingCart-…`, `Order-…`, `Payment-…`, `Shipment-…` in `messages`;
  - in the Kafka UI, as messages on `orders-internal`, `payments-requests`, `payments-responses`, `shipments-requests`, `shipments-responses`, with the routing key as the record key;
  - in pgAdmin, as rows in the views and in `processor_checkpoints`;
  - on the **Purchases** dashboard, as one more order completed.
- The attendee says what each tool shows that the others don't.

### AC 1.2: Answer questions from the documentation only

- **Given** EventCatalog, Springwolf and Swagger are open, and the code is not.
- **When** the attendee answers:
  - who reads `PaymentFailed`;
  - which queue binds `payments-responses`, and with which pattern;
  - which team owns shipments;
  - what *capture* means in payments;
  - which endpoint cancels an order, and what it answers.
- **Then** the answers are:
  - the orders module;
  - `orders-payments-responses` with `ecommerce.order.*`;
  - the owner listed on the service page;
  - the dictionary entry in `ubiquitous-language.mdx`;
  - `POST /customers/{customerId}/orders/{orderId}/cancel`, which answers `202`.
- Each answer points to the page it came from.

### AC 1.3: Check what the LLM says

- **Given** the LLM is set up with the Grafana MCP, our Spring MCP and the EventCatalog documents as files.
- **When** the attendee asks it to explain the purchase flow of their own order.
- **Then** the attendee marks each claim as confirmed or wrong, using a span, an event or a catalog page. Example of a wrong claim to look for: "the shop charges the customer when the order is placed". The capture comes after `PackageWasSent`.

---

## 2. `correlation`

### AC 2.1: Two correlation ids in one purchase

- **Given** the attendee's completed purchase from AC 1.1.
- **When** the attendee reads `correlationId` and `causationId` in `message_metadata` of the order's events in pgAdmin (or `$correlationId` and `$causationId` in KurrentDB, when it runs), and the `messaging.message.correlation_id` of the `handle …` spans in Tempo.
- **Then** the attendee finds two correlation ids:
  - one from **Check out**, shared by `ShoppingCartCheckedOut`, `PlaceOrder`, `OrderPlaced`, `AuthorizePayment` and `ReserveStock`;
  - one from **Pay now**, continued by the provider's `ConfirmPaymentAuthorization`, because commands from outside take over the ids of the last event in the stream they change.
- Each message's causation id is the id of the message above it.
- Fixed in the sample: `MessageContext` keeps the ids in the tracing baggage, and the stores and brokers carry them.

### AC 2.2: Find where the trace breaks

- **Given** `WORKSHOP_SCENARIO=correlation`. The scenario strips the trace headers from messages on `payments-responses` for customer `Ben` **[new]**.
- **When** the shopper buys as `Ben`.
- **Then** Ben's purchase shows as two separate traces: the trace of Pay now stops before `handle PaymentAuthorized`, and a new trace starts there.
- The attendee diagnoses it in two steps:
  - the correlation ids in `message_metadata` and in the Kafka headers still link the messages;
  - the `traceparent` header is missing on `payments-responses` in the Kafka UI.
- The conclusion: trace context and correlation ids travel separately; one can break while the other holds.
- To fix: the scenario is switched off. The tests that would catch it are `expectCorrelatedTrace` (fails) and `expectCausation` (passes), shown in exercise 9.

---

## 3. `idempotency`

### AC 3.1: A command sent twice

- **Given** an order of the attendee that waits for the payment.
- **When** the attendee publishes `RecordOrderPaymentAuthorization` with the order's `paymentId` twice in Springwolf.
- **Then** the first creates `OrderPaymentAuthorized`. The second `command.handle` span shows the same stream version before and after, and no event appended.
- Fixed in the sample: the decider returns `noEvents` for a state that already has the authorization (`Order.recordPaymentAuthorization`, quoted in the README).

### AC 3.2: Double clicks and late actions by a person

- **Given** an order that waits for the payment.
- **When** the attendee clicks **Pay now**, goes back, and clicks **Pay now** again. Then, after paying and before the order is confirmed or after it is, the attendee clicks **Cancel order**, or calls the cancel endpoint in Swagger after the confirmation.
- **Then:**
  - both Pay now clicks open the same Checkout Session URL, so one payment can't be authorized twice;
  - a cancel after the confirmation answers `202`, and the order stream gets nothing new.
- The attendee explains why a `202` and not an error: the order is in the middle of the process, and the customer sees the result on the order page.

### AC 3.3: A late answer about an old payment

- **Given** an order whose first payment failed and that the customer paid again with **Retry payment**. In the workshop profile, a payment times out after a short time **[new]**.
- **When** the attendee publishes `PaymentFailed` for the first `paymentId` in Springwolf.
- **Then** the order appends nothing.
- Fixed in the sample: `RecordOrderPaymentFailure` carries the `paymentId`, and the order ignores any payment but its current one.

### AC 3.4: The same checkout twice

- **Given** a cart that was checked out.
- **When** the attendee publishes the external `ShoppingCartCheckedOut` of that cart again in Springwolf.
- **Then** there is still one order: the saga sends the same `PlaceOrder` with the order id derived from the cart id, and the order appends nothing.
- The attendee explains what would happen with a random order id: two orders, shipped and billed twice (Bellemare's type 8).

### AC 3.5: Find the duplicates nobody noticed

- **Given** `WORKSHOP_SCENARIO=idempotency`. The scenario publishes every `PaymentAuthorized` of customer `Chloe` twice **[new]**.
- **When** the shopper buys as `Chloe`.
- **Then** the shop works, and the ticket asks: "Finance asks if anyone was charged twice."
- The attendee proves the duplicates came and were absorbed:
  - two `handle PaymentAuthorized` spans with the same message id;
  - two `RecordOrderPaymentAuthorization` commands, the second with no appended event;
  - one `PaymentCaptured` per order in `messages`.
- Fixed in the sample: derived ids and idempotent deciders.
- Contrast: the cart throws over HTTP (`409`, `412`), because a person has to see the error.

---

## 4. `delivery-guarantees`

### AC 4.1: At-least-once after a crash

- **Given** `WORKSHOP_SCENARIO=delivery-guarantees`. The payments forwarder fails once after it published `PaymentAuthorized` for customer `Dan`, and before it stores its checkpoint **[new]**.
- **When** the shopper buys as `Dan`.
- **Then** `PaymentAuthorized` is published twice with the same message id. The second handling appends nothing.
- The attendee sees:
  - the error in the `forward PaymentExternalEventForwarder` span;
  - the subscription `payments-external` restarting from its checkpoint in `processor_checkpoints`;
  - the two publications in the Kafka UI.
- Fixed in the sample: the forwarder publishes before storing the checkpoint, the message ids are derived, and the handlers are idempotent.

### AC 4.2: The event store as the outbox when Kafka is down

- **Given** the same scenario, and the shopper runs.
- **When** the trainer turns on "Kafka unreachable" at run time **[new]**: every publish fails for three minutes. Then the trainer runs `docker compose stop kafka` for one minute and starts it again, to show the real client errors.
- **Then** the customers can still check out, because the cart's append succeeds, but the orders don't appear.
- The attendee sees:
  - the **Subscription lag** growing;
  - errors in the `forward …` spans and in Loki;
  - the events in `messages`, while the checkpoints in `processor_checkpoints` stay put;
  - with the real outage: connection errors and the 10-second publish timeout (`messaging.kafka.publish-timeout`) in the logs.
- After the switch is off, or Kafka is back, the lag drops to zero and every waiting order is placed. No event is lost.
- Fixed in the sample: the event store is the outbox, and the forwarders keep their checkpoints until the publish succeeds.

### AC 4.3: A poison message on the bus

- **Given** the scenario makes the handler of `ReserveStock` fail every time for customer `Eva` **[new]**.
- **When** the shopper buys as `Eva`.
- **Then** Eva's order stays in Processing. **Dead letters** fires.
- The attendee finds:
  - the retries with back-off in the trace;
  - the message in the `shipments-requests-dlt` topic in the Kafka UI;
  - its queue and type on the **Dead letters** panel.
- To fix: the scenario's fault is turned off. The attendee replays the message from the DLQ by publishing it again with Springwolf, and the order continues.
- Discussion: `MessageFailureStrategy` retries unless the message is unreadable. Bellemare warns that a DLQ suits independent messages only.

### AC 4.4: A poison event in a store subscription

- **Given** the scenario makes the order status view fail on `OrderConfirmed` for customer `Finn` **[new]**.
- **When** the shopper buys as `Finn`, then as others.
- **Then** the order pages of every customer stop updating after Finn's event, not only Finn's. **Views falling behind** fires.
- The attendee finds:
  - the error in the `project …` span;
  - the subscription retrying from the same checkpoint;
  - no dead-letter queue for store subscriptions.
- To fix: the fault is turned off, and the subscription catches up from its checkpoint.
- Discussion: why a subscription has to keep the order, and what a skip would cost.

---

## 5. `concurrency-and-consistency`

### AC 5.1: Two tabs on one cart

- **Given** the attendee has a cart with two products, open in two browser tabs.
- **When** the attendee removes a product in tab 1, then clicks **Check out** in tab 2.
- **Then** the checkout is refused. The API answers `412 Precondition Failed`, because tab 2 sent the old version in `If-Match`.
- The attendee sees the version in the `ETag` in Swagger, and the conflict on **Append conflicts**.
- Fixed in the sample: optimistic concurrency with the expected version. A person's command isn't retried.

### AC 5.2: Answers in the reverse order, and a cancel during the authorization

- **Given** `WORKSHOP_SCENARIO=concurrency-and-consistency`. The scenario delays `StockReserved` for customer `Gaby` by 20 seconds, so the payment authorization arrives first **[new]**. It also lets the shopper click **Cancel order** for customer `Hugo` at the moment the authorization arrives **[new]**.
- **When** the shopper buys as `Gaby` and `Hugo`.
- **Then:**
  - Gaby's order is confirmed once both reservations are in, in either order;
  - Hugo's order ends either canceled with the payment canceled, or confirmed with the cancel ignored, never in between;
  - version conflicts on the `Order-…` streams show on **Append conflicts**, and `command.handle` spans repeat.
- Fixed in the sample: `CommandHandler` retries a conflict up to three attempts, which is safe because the deciders are idempotent, and the order waits for both answers.

### AC 5.3: A slow view

- **Given** the scenario slows the order status view by 2 seconds per event for customer `Ida` **[new]**.
- **When** the attendee checks out as `Ida` and calls `GET /customers/{customerId}/orders/{orderId}` in Swagger right after, then again with the `ETag` in `If-None-Match`.
- **Then:**
  - the first call waits up to one second and answers `503` with `Retry-After: 1`;
  - later calls answer the order with an `ETag`, then `304` until something changes;
  - the shop page says "Your order is being placed" and updates itself.
- The attendee finds `LongPolling` and `ConditionalRead`, and the lag on **Subscription lag**.
- Fixed in the sample: long polling on the server, with a deadline. Discussion: why not blind retries on the client.

---

## 6. `compensation-and-deadlines`

### AC 6.1: Out of stock and a rejected fulfillment

- **Given** no scenario; the attendee shops by hand.
- **When** the attendee buys "Limited edition trail shoes", which is out of stock from the start. Then the attendee pays another order and clicks **Reject** on the sandbox warehouse.
- **Then:**
  - the first order records the shipment failure but is canceled only once the payment has answered: after the attendee pays, it is canceled at once with `CancelPayment`; if nobody pays, it is canceled when the payment times out, with nothing to cancel;
  - the second order is canceled with both `CancelPayment` and `ReleaseStock`;
  - `OrderCanceled` in `messages` carries `paymentStatus` and `shipmentStatus`, and the saga only reads them.
- Fixed in the sample: the order decides what to release.
- Checked in the code and tests: `OrderDecider.progress` returns nothing while the shipment failed and the payment is `Pending` or `Retrying`, and cancels once the payment answers, with reason `PaymentFailed` if it failed. Tests: `PurchaseTests.productsOutOfStockAreRecordedAtCheckoutWhileThePaymentIsStillPending` (only `RecordOrderShipmentFailure` follows), `PurchaseTests.productsOutOfStockWaitForThePaymentThenCancelTheOrderAndTheHoldWithoutCharging` and `OrderTests.anOutOfStockShipmentWithTheCardAlreadyAuthorizedCancelsAndCancelsThatHold` (authorized later, then canceled with `CancelPayment`), `OrderTests.bothParticipantsFailingCancelsTheOrderOnce` (payment failed first, then out of stock). Not found: an `OrderTests` case for out of stock while the first payment is pending, and one for out of stock first, then the payment timing out.

### AC 6.2: The provider refuses the capture

- **Given** `WORKSHOP_SCENARIO=compensation-and-deadlines`. The sandbox refuses the capture for customer `Jan` **[new]**.
- **When** the shopper buys as `Jan`, and the warehouse sends the package.
- **Then** Jan's order stays unpaid after the package left. **Capture failed** fires.
- The attendee finds:
  - `FailPaymentCapture(UnexpectedError)` and `PaymentCaptureFailed`;
  - `PaymentFailed` to the order, and `OrderPaymentFailed`;
  - **Retry payment** on the order page.
- Paying again sends the capture at once, and the order completes. If nobody pays by `dueAt`, the order ends `ClosedUnpaid`, and **Money lost** fires.

### AC 6.3: A webhook that never arrives

- **Given** the workshop profile sets a short authorization hold, for example 3 minutes **[new]**, and the sandbox warehouse never answers for customer `Kim` **[new]**.
- **When** the shopper buys and pays as `Kim`.
- **Then** Kim's order stays in Processing. After the hold expires, `AuthorizationExpiryWorker` sends `ExpirePaymentAuthorization`, the order is canceled, and the products are released.
- The attendee finds the worker's span within a minute after `captureBefore`, and the reason in `OrderCanceled`.
- Fixed in the sample: deadlines and workers, so nothing waits forever ("No, it can never happen!").

---

## 7. `versioning`

### AC 7.1: An old message shape on the bus

- **Given** the sample has a v2 of one external event, for example a renamed field in `PaymentAuthorized`, with a mapping from v1 in `MessageMapping` **[new]**.
- **When** the attendee publishes a v1 message (the old type name and shape), from a prepared example or in Springwolf.
- **Then** the orders module reads it as v2 and the order continues.
- With the mapping removed (scenario switch **[new]**), the message is dead-lettered with `UnreadableMessageException`, and **Dead letters** fires.
- To fix: register the old name with a transformation (`register(oldName, CurrentClass, jsonTransform)`).

### AC 7.2: An old stored event

- **Given** an order stream holds events written in the v1 shape before the change **[new]**.
- **When** the order is read to handle the next command.
- **Then** the event store reads the old event as the current class through the same mapping, and the decider sees the right state. Without the mapping, the read fails and names the type.

### AC 7.3: The contract test catches the change

- **Given** a JDK.
- **When** the attendee renames a field in an external event and runs `./gradlew test --tests '*ContractTests'`.
- **Then** the Strictland test fails with the difference and writes `.snap.received.json`.
- The attendee decides: accept the snapshot and add a mapping for the old shape, or keep the old shape.
- For attendees without a JDK, the trainer shows this.

---

## 8. `incidents`

### AC 8.1: A symptom ticket only

- **Given** `WORKSHOP_SCENARIO=incident-NN`, an alias of a fault from exercises 2 to 6, or a fault only used here, such as a projection that counts a repeated event twice **[new]**.
- **When** the attendee gets only the ticket, for example "Revenue on the dashboard is higher than the money we captured."
- **Then** the attendee names the cause, the evidence and the fix:
  - the evidence: **Revenue** against the `PaymentCaptured` events in `messages`, and the projection's span on the repeated event;
  - the fix: turn off the fault, then rebuild the view by resetting its checkpoint in `processor_checkpoints`.
- The attendee also explains the README's limitation, "A view can come back after a replay".

### AC 8.2: The LLM against the evidence

- **Given** the same incident, and the LLM with Grafana MCP and our Spring MCP **[new]**.
- **When** the attendee asks the same question twice: once with Grafana MCP only, and once with our timeline tool `order_timeline(orderId)`.
- **Then** the attendee compares the two answers with the evidence, and notes which claims were wrong or not backed by data.

---

## 9. `tests`

### AC 9.1: A test checks the trace

- **Given** a JDK and Docker, and the Compose Grafana running.
- **When** the attendee runs one `PurchaseTests` scenario, for example `theProvidersConfirmationConfirmsTheOrderAndRequestsFulfillmentInThePayNowTrace`, on Kafka.
- **Then** the test passes, and its trace shows in Grafana (the tests send telemetry to the Compose Grafana when it runs). The attendee reads `expectTrace` and `expectCorrelatedTrace` against that trace.

### AC 9.2: The test that fails, and the one that doesn't

- **Given** the propagation break from AC 2.2, turned on in the test setup.
- **When** the attendee runs the scenario with `expectCorrelatedTrace` and `expectCausation`.
- **Then** `expectCorrelatedTrace` fails, because the spans no longer form one tree, while `expectCausation` passes, because the ids are still right. The attendee explains why both checks exist.

### AC 9.3: A browser test that troubleshoots

- **Given** a JDK, Docker and Chromium (`./gradlew e2eTest`).
- **When** the attendee runs `ShopTelemetryInBrowserTests`.
- **Then** the test shops in Chromium and finds the purchase in Grafana as one trace, as metrics and as logs.
- For attendees without a JDK, the trainer shows this.

---

## What the sample needs

- The `workshop` Compose profile: the app image, the shopper, EventCatalog as a static site, a Kafka UI, pgAdmin.
- The scenario switch: `WORKSHOP_SCENARIO` at start, a run-time switch for some faults, aliases `incident-NN`.
- The faults, as decorators of `MessageBus` and `EventStore`, keyed on message type and customer: strip trace headers, duplicate, fail a forwarder once after publish, fail publishing for a time, fail a handler every time, fail a projection on one event, delay, slow a projection, count a repeated event twice.
- Sandbox faults: refuse a capture, never answer a fulfillment request.
- Settable deadlines in the workshop profile: payment validity, authorization hold, payment terms.
- The shopper: named customers, scripted per scenario.
- A real v2 of one external event and one stored event, with mappings from v1.
- Our Spring MCP server with three read-only tools: a stream with metadata, the subscriptions with their checkpoints, the timeline per order (version 1.0, Q19).

Version 1.1 (Q15): exercise 7 `versioning` and its real v2 messages.
