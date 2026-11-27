# Codebase hooks

Checked on 27 November 2026 by Claude in the main session. The claims below were checked in the code. The full list of exercise ideas built on them is in `../exercises-from-the-repo/findings.md`, and the decorator limits in `../fault-injection/findings.md`.

Paths are under `samples/e-commerce/src/main/java/io/eventdriven/`.

- `MessageBus` has `publish`, two `subscribe` forms, `use(middleware)` and `whenCaughtUp` (`core/messaging/MessageBus.java`). Middleware sees each published message before the queues; it can observe or throw, but not drop, delay or duplicate.
- Message ids published while handling a message are derived from the handled message id, type name and position; HTTP-started messages get a random id (`core/messaging/MessageContext.java`, `inheritForPublished`, `derivedId`, `newId`).
- Event ids are random at append in all three stores (`InMemoryEventStore.java:97`, `KurrentDBEventStore.java:210`, `PostgreSQLEventStore.java:270`).
- Kafka: 3 partitions per topic, routing key as the record key, `<queue>-dlt` dead-letter topics, `messaging.dead_lettered` counter (`core/messaging/kafka/KafkaMessageBus.java`). `MessageFailureStrategy.retryUnlessUnreadable` decides retry or dead-letter.
- Kafka bus is a `SmartLifecycle` bean, so a decorator has to forward the lifecycle.
- Store subscriptions retry with a back-off and have no dead-letter queue (`core/eventstore/kurrentdb/KurrentDBSubscriptionToAll.java`, README "Delivering messages reliably").
- The bus and the store are chosen by `messaging.bus` and `eventstore.type` in `ecommerce/ECommerceApplicationConfig.java`.
- Sandboxes keep their state in memory (`payments/sandbox/SandboxPaymentGateway.java`, `shipments/sandbox/SandboxWarehouse.java`). `SandboxPaymentGateway.capture` always publishes `ConfirmPaymentCapture`; the sandbox hold is a fixed 7 days.
- Deadlines are fixed in code: `Payment.Validity` 24 hours (`PaymentsConfig`), `PaymentTerms` 7 days (`OrdersConfig`). The three workers run every minute (`@Scheduled(fixedRate = 1, timeUnit = MINUTES)`).
- Prices are random per run (`shoppingcarts/pricing/RandomProductPriceCalculator.java`, `ThreadLocalRandom`).
