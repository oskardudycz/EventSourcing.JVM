# Breaking the system on purpose

Checked on 27 November 2026 by Claude in the main session. Decision so far (`../qa.md`, Q4): a decorator around `MessageBus` and `EventStore`, chosen by a property, is the main way. The sandboxes simulate the outside failures. External tools only where they add something.

## What a decorator can and can't do

Paths are under `samples/e-commerce/src/main/java/io/eventdriven/`.

- It sees every `publish`, `subscribe` and handler call of the bus (`core/messaging/MessageBus.java`), and every append and subscription of the store. So it can drop, duplicate, delay, reorder, fail or corrupt one message type for one business id.
- Kafka and RabbitMQ buses are Spring `SmartLifecycle` beans (`KafkaMessageBus implements MessageBus, SmartLifecycle`). A decorator bean has to forward `start`, `stop` and `isRunning`, or the consumers never start.
- Message ids are derived per handled message (`core/messaging/MessageContext.java`), but event ids in all three stores are `UUID.randomUUID()` at append (`InMemoryEventStore.java:97`, `KurrentDBEventStore.java:210`, `PostgreSQLEventStore.java:270`). A decision that repeats on every machine must use the message type and business ids (customer name → customer id → order id), not message or event ids.

## Kafka downtime with the decorator

The decorator can make the app behave as if Kafka were down:

- `publish` throws for a while. Today a publish waits up to `messaging.kafka.publish-timeout=10s` and then fails (`KafkaMessageBus.publish`, `application.properties`). A forwarder publishes first and stores its checkpoint after, so a failed publish keeps the checkpoint, and the store delivers the event again later (README, "Delivering messages reliably"). That's the outbox at work, and participants see the lag grow and then catch up.
- Handlers on the bus get nothing for a while (the decorator holds or rejects deliveries), which looks like a consumer outage.

What it doesn't show: the real client behaviour (connection errors in the logs, the 10-second publish timeout, reconnects, consumer group rebalances, offsets). For that, stop the real broker: `docker compose stop kafka` or `docker compose pause kafka`, then start it again. No extra tool is needed, and the trainer controls when.

## External tools and what they add

| Tool | Adds | Limits for this sample |
|---|---|---|
| `docker compose stop/pause <service>` | Real outage of Kafka, KurrentDB or PostgreSQL | Whole service only; timing done by hand |
| Toxiproxy ([Shopify/toxiproxy](https://github.com/Shopify/toxiproxy), v2.13.0) | Latency, timeouts, connection resets on a TCP link | Kafka clients connect to the advertised listeners returned in metadata, so they bypass the proxy unless Kafka advertises the proxy's address (a known problem; no confirmed working setup found: <https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/73714385/toxiproxy-for-kafka-in-a-docker-compose-file>). Simpler for PostgreSQL and KurrentDB |
| Pumba ([alexei-led/pumba](https://github.com/alexei-led/pumba), 1.6.0) | Kills, pauses, network delay on containers | Random by design; overlaps with `docker compose` for an outage |
| Chaos Monkey for Spring Boot (v4.0.0) | Latency and exceptions on Spring beans | Counts calls ("every Nth"), so not repeatable across machines; handlers are not separate beans (`../tools-and-versions/findings.md`) |

## Reference: how the OpenTelemetry demo switches failures

The OpenTelemetry Astronomy Shop demo ([open-telemetry/opentelemetry-demo](https://github.com/open-telemetry/opentelemetry-demo), 3.1.0) turns failures on with feature flags in flagd: for example `paymentFailure` (errors for a share of charge calls), `kafkaQueueProblems` (a consumer delay, so lag grows) and `loadGeneratorVUs` (traffic). It ships a load generator so the failures show in the telemetry (<https://opentelemetry.io/docs/demo/feature-flags/>). The same shape fits here: one switch per scenario, plus example traffic that the scenario sets up.
