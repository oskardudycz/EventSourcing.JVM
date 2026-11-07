# Exercise 03 - Business Processes: Solutions

The [exercise](../../../../../../../../exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses/README.md) asks you to model the Group Checkout process. Here you'll find several ways to do it, each with its own `BusinessProcessTests`:

- [Transactions](./transactions): all guests checked out in one database transaction.
- [Choreography](./choreography).
- [Todo List](./todolist): a read model keeps each guest's checkout status; once none is pending, it records the group checkout as completed or failed.
- [Process Managers](./processmanagers): the group checkout keeps the process state, and decides which commands and events to send next.
- [Sagas](./sagas): a stateless saga turns events into commands, on the in-memory message bus.

Sagas come in two flavours: [aggregates](./sagas/solution1_aggregates) and [immutable entities with functional deciders](./sagas/solution2_immutableentities).

## Sagas on a real broker

The same saga runs on [Kafka](./sagas_kafka) and [RabbitMQ](./sagas_rabbitmq). Testcontainers starts the broker, so you only need Docker running. Besides `BusinessProcessTests`, each variant has:

- `MessageMetadataPropagationTest`: the correlation ID stays the same through the whole process, and the causation ID is the ID of the message whose handler published the next one.
- `KafkaErrorHandlingTest` / `RabbitMQErrorHandlingTest`: what happens when a handler fails or a message can't be read.

## When a handler fails

A failing handler doesn't lose the message. The broker adapter:

1. retries it 3 times with exponential back-off and jitter ([RetryBackOff](./core/RetryBackOff.java)): about 1 s, then about 2 s;
2. then moves it to a dead-letter destination:
   - Kafka: `<topic>-dlt`, published by Spring Kafka's `DeadLetterPublishingRecoverer`;
   - RabbitMQ: the message is rejected, and the broker routes it through `<exchange>-dlx` to `<exchange>-saga-dlq`;
3. and carries on with the next message.

What to do with each failure is decided in one place: [MessageFailureStrategy](./core/MessageFailureStrategy.java). It gets the message type, the message (empty if it couldn't be read) and the error, and answers `RETRY` or `DEAD_LETTER`. The default retries everything except messages it couldn't read: a retry won't fix broken JSON or an unknown type.

To write your own, ask in this order:

1. **Is it a business failure?** Then it shouldn't reach the strategy at all. Record an event (e.g. `GuestCheckoutFailed`) and let the process react.
2. **Will a retry change the outcome?** Timeouts, lost connections, concurrency conflicts: probably. Unreadable messages and bugs: never.
3. **Is it safe to run the handler again?** Only if it's idempotent.
4. **Does order matter for this message?** Dead-lettering skips it, so later messages for the same entity go first. If that corrupts state, stopping is safer than skipping.
5. **Can we afford to lose it?** A notification or a rebuildable projection: maybe. A step of a business process: never.

### Extending it

The strategy shows two decisions. Two more are common:

- **STOP**: stop consuming until someone looks, when skipping a message would break ordering. Kafka has `CommonContainerStoppingErrorHandler`; on RabbitMQ, stop the listener container.
- **SKIP**: log and acknowledge, for messages you can afford to lose.

Retries here are blocking: Kafka holds the partition, RabbitMQ holds the consumer, while they wait. For longer waits use non-blocking retries: Kafka retry topics (`@RetryableTopic`) or a RabbitMQ queue with a TTL that dead-letters back to the main one.

### A dead-letter queue is not a solution

Putting a message aside only helps if someone deals with it. Until then, the process that waits for it is stuck. You need:

- **monitoring**: both adapters count `messaging.dead_lettered`, tagged with the message type and the source topic or queue. Alert on it, or on the dead-letter queue's depth;
- **enough to triage**: Kafka adds headers with the exception, the original topic, partition and offset, and the delivery attempt. RabbitMQ adds `x-death`;
- **a decision**: replay it, fix and replay it, or discard it. Decide who does that and how, before it happens in production. For a process, consider a timeout that compensates instead of waiting forever.

## Seeing it

The tests can send traces and metrics to [Grafana LGTM](https://github.com/grafana/docker-otel-lgtm). From the `solved` folder:

```shell
docker compose --profile observability up -d
./gradlew test -Pobservability --tests '*MessageMetadataPropagationTest'
```

Open http://localhost:3000, go to Explore, then:

- **Tempo**: search for the service `event-driven-architecture-workshop`. Each Group Checkout is one trace. It starts with the clerk's action, `GroupCheckoutFacade#initiateGroupCheckout`, then follows each message published, received, handled, and the next one published. Facades are traced with Micrometer's `@Observed`. Once the first message is handled, spans carry `correlationId` and `causationId`. Run the error-handling tests to see retries and the dead-letter publish.
- **Prometheus**: query `messaging_dead_lettered_total`.

New traces can take up to half a minute to show up in Tempo's search. A plain `./gradlew test` doesn't export anything. Stop the container with `docker compose --profile observability down`.
