# Exercise outline

Agreed in Q14 in `../qa.md`: nine exercises, concepts grouped, each with several tasks. The README-to-exercise table at the end uses these names.

The LLM with MCP servers is a tool in every exercise, not an exercise of its own. Each exercise has one scenario (`WORKSHOP_SCENARIO=<topic name>`), which can turn on several faults, each for a different fixed customer.

| # | Exercise | Tasks | Scenario faults |
|---|---|---|---|
| 1 | `tools` | One purchase by hand, then find it in every tool (shop, Swagger, Springwolf, EventCatalog, Grafana, Kafka UI, pgAdmin); answer questions from EventCatalog, Springwolf and Swagger only (who reads `PaymentFailed`, which queue binds `payments-responses`, what *capture* means); ask the LLM to explain the flow and check each claim | none; the shopper runs |
| 2 | `correlation` | Follow the correlation and causation ids of one purchase: two correlation ids, the provider's answer continues Pay now, where the ids live (`message_metadata` in the PostgreSQL event store, broker headers, baggage); then find why one trace breaks | trace headers lost on one channel |
| 3 | `idempotency` | Send a command twice in Springwolf; click Pay now twice and Cancel after confirmation; send a late `PaymentFailed` for an old payment; publish `ShoppingCartCheckedOut` twice; find the duplicated `PaymentAuthorized`; compare with the cart, which throws | duplicated `PaymentAuthorized` for one customer |
| 4 | `delivery-guarantees` | A message published twice after a forwarder crash (at-least-once); Kafka "down" while orders keep coming (event store as outbox), plus `docker compose stop kafka` for the real errors; a poison message on the bus (retry, `<queue>-dlt`, replay); a poison event in a store subscription (no dead-letter queue, views stop) | forwarder fails after publish; publish fails, run-time switch; poison message; poison event |
| 5 | `concurrency-and-consistency` | Two tabs on one cart (`412`); answers in reverse order and the customer cancelling while the authorization arrives (version conflicts, retries); a slow view (long polling, `304`, `503`) | one module delayed; one projection slowed |
| 6 | `compensation-and-deadlines` | Out of stock, rejected fulfillment, refused capture after the package left; a lost webhook and the worker that unblocks the order | sandbox refuses a capture; sandbox drops a webhook; short deadlines |
| 7 | `versioning` | An old shape of a message on the bus and of a stored event; contract tests | old shapes published (needs a real v2 in the sample) |
| 8 | `incidents` | Tickets with symptoms only, started by alias (`incident-01` ...); the LLM with Grafana MCP and our Spring MCP (stream with metadata, subscriptions, timeline) | mixes of the faults above, plus a projection bug that counts duplicates |
| 9 | `tests` | `PurchaseTests` with `expectTrace`, telemetry from tests in Grafana, a browser test (needs a JDK; demo for others) | none |

## Idempotency in the README, and the exercise that shows it

| README says | Where | Exercise |
|---|---|---|
| "Every decision is idempotent: a command that arrives twice, or too late, decides no events" | The order decides, the saga only translates | `idempotency` |
| Modules ignore a message they can't use, so the process keeps moving; the cart throws because a person must see the error | same section | `idempotency`, `concurrency-and-consistency` |
| A version conflict is retried up to three attempts, safe because the deciders are idempotent | same section | `concurrency-and-consistency` |
| The saga derives ids, so the same event twice sends two identical commands | same section | `idempotency` |
| A second click on Pay now before the expiry gets the same page | Where the steps come from | `idempotency` |
| The order ignores answers about any payment but its current one | When a step fails | `idempotency` |
| A wake-up that comes too early, or twice, appends nothing | Deadlines and workers | `compensation-and-deadlines` |
| A projection skips an event its version has passed; the summary and the forwarder share one checkpoint | The projections behind the external events | `delivery-guarantees`, `incidents` (projection bug) |
| A forwarder publishes, then stores the checkpoint; a crash between publishes again, and the handlers absorb it | Delivering messages reliably | `delivery-guarantees` |
| A handler that runs again after a redelivery writes its messages with the same message ids | Seeing what happens | `delivery-guarantees`, `correlation` |
| A deleted document keeps no version, so a replay can bring a view back | What this sample doesn't cover | `incidents` (projection bug) |

The sample has no table of processed message ids: idempotency comes from the state the decider sees and from derived ids, not from deduplication by message id.
