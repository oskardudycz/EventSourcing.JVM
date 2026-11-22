# Broker setup review: findings

This review checks whether the Kafka and RabbitMQ setup of the event-driven architecture workshop is idiomatic and safe enough to be the base for the message bus of `samples/distributed-processes`.

## Scope and method

Paths use these short forms: `W/` is `workshops/event-driven-architecture/`; `e03/` is `workshops/event-driven-architecture/solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses/`; `props` is `workshops/event-driven-architecture/solved/src/test/resources/application.properties`.

Library versions come from the Gradle cache that the workshop build uses: Spring Boot 4.1.2, Spring for Apache Kafka 4.1.2, kafka-clients 4.2.2, Spring AMQP 4.1.2, amqp-client 5.30.0, Micrometer Tracing OTel bridge 1.7.2.

Evidence comes from the official documentation pages listed with each finding. Where the documentation does not say something, I checked the library class files with `javap` and say so. I did not run the tests or start any broker.

Status: **Unsafe (must fix)**: the setup can lose, duplicate without idempotency, or reorder messages, or it has a security hole. **Not idiomatic (should change)**: it works, but Spring or the broker has a better or built-in way, or it does not fit the target bus. **Fine (keep)**.

## Summary

| ID | Status | Topic | Where |
|---|---|---|---|
| U1 | Unsafe | Kafka `auto-offset-reset=latest` skips messages for new or expired consumer groups | `props:10` |
| U2 | Unsafe | Trusted packages default to `*` and the type mapping is empty | `e03/sagas_kafka/core/versioning/MessageSerializationConfiguration.java:33,38`, `e03/sagas_rabbitmq/core/RabbitConfig.java:39,50` |
| U3 | Unsafe | Kafka creates unknown topics on publish, so a wrong channel name loses messages silently | `e03/sagas_kafka/core/KafkaMessageBus.java:61-65`, `KafkaConfiguration.java:35-46`, `W/docker-compose.yml:75-108` |
| U4 | Unsafe | RabbitMQ dead-lettering from a classic queue is at-most-once | `e03/sagas_rabbitmq/core/RabbitConfig.java:67-72,81-95,118-133` |
| N1 | Not idiomatic | Kafka publish timeout is shorter than the producer's own timeouts | `props:16`, `KafkaMessageBus.java:65` |
| N2 | Not idiomatic | The dead-letter `KafkaTemplate` has no observation registry | `KafkaConfiguration.java:84-89` |
| N3 | Not idiomatic | `messageId` is a new random UUID on each publish | `e03/sagas_kafka/core/messages/MessageMetadata.java:28`, `e03/sagas_rabbitmq/core/messages/MessageMetadata.java:28` |
| N4 | Not idiomatic | Kafka key comes from the first record component by reflection; RabbitMQ key is the class name | `e03/core/MessageRouter.java:37-52`, `KafkaMessageBus.java:52-54`, `RabbitMQMessageBus.java:51-54` |
| N5 | Not idiomatic | One dead-letter destination per channel, not per consumer queue | `KafkaConfiguration.java:40-46`, `RabbitConfig.java:81-95` |
| N6 | Not idiomatic | `mandatory=true` turns "no subscriber" into a publish failure | `props:20`, `RabbitMQMessageBus.java:129-131` |
| N7 | Not idiomatic | RabbitMQ publishers share the connection with consumers | `props:18-22` |
| N8 | Not idiomatic | Two identical copies of the versioning code, and a stray `eventType` fallback | `e03/sagas_*/core/versioning/*`, `e03/sagas_rabbitmq/core/VersionedMessageConverter.java:39-45` |
| F1-F12 | Fine | Producer defaults, sync send, offset commits, deserialization, error handlers, confirms, persistence, ack mode, observation, baggage, versioning, Compose | see below |

## Unsafe (must fix)

### U1. Kafka `auto-offset-reset=latest` skips messages

**Where:** `props:10` sets `spring.kafka.consumer.auto-offset-reset=latest`. `e03/sagas_kafka/core/KafkaErrorHandlingTest.java:47` overrides it to `earliest`, because with `latest` the test can miss its own messages.

**What happens:** `auto.offset.reset` applies "when there is no initial offset in Kafka or if the current offset does not exist any more on the server". With `latest`, the consumer jumps to the end. (1) A new consumer group skips every message published before it first got its partitions. In the target, a queue is a consumer group, so a new queue loses what is already in the topic. That includes the first start, if the forwarder publishes before the listener joins. (2) Kafka deletes the committed offsets of an empty group after `offsets.retention.minutes` (default 10080, 7 days). After a longer stop, the group restarts at the end and skips everything published in between. RabbitMQ does not have this problem once the durable queue and binding exist.

**Evidence:** Kafka consumer configs (`auto.offset.reset`, default `latest`): https://kafka.apache.org/42/configuration/consumer-configs/ . Kafka broker configs (`offsets.retention.minutes`, default 10080): https://kafka.apache.org/42/configuration/broker-configs/ . Spring Cloud Stream Kafka binder: "If the consumer group is set explicitly ... 'startOffset' is set to `earliest`. Otherwise, it is set to `latest` for the `anonymous` consumer group." https://docs.spring.io/spring-cloud-stream/reference/kafka/kafka-binder/config-options.html

**Change:** Set `spring.kafka.consumer.auto-offset-reset=earliest`. Handlers are idempotent, so a replay is safe.

### U2. Trusted packages default to `*`, and the type mapping is empty

**Where:** `e03/sagas_kafka/core/versioning/MessageSerializationConfiguration.java:33` reads `${app.messaging.trusted-packages:*}`, and line 38 passes it to `DefaultJacksonJavaTypeMapper.addTrustedPackages`. `e03/sagas_rabbitmq/core/RabbitConfig.java:39` does the same, and line 50 passes it to `DefaultClassMapper.setTrustedPackages`. No properties file sets the property. `e03/sagas_kafka/solution1_aggregates/GroupCheckoutMessageTypeMapping.java:12` returns an empty `MessageMapping`, so types resolve from the Java class name in `__TypeId__`.

**What happens:** The consumer creates an instance of any class that the header names. This is the serialization gadgets attack, which the code comment already mentions.

**Evidence:** Spring AMQP: "`trustedPackages` option to overcome Serialization Gadgets vulnerability. By default, the `JacksonJsonMessageConverter` trusts only to `java.util` and `java.lang` packages." (CVE-2017-4995) https://docs.spring.io/spring-amqp/reference/amqp/message-converters.html . Spring Kafka `TRUSTED_PACKAGES` default and type mappings: https://docs.spring.io/spring-kafka/reference/kafka/serdes.html#serdes-mapping-types

**Change:** Map every message type to a stable name with `setIdClassMapping`, and trust only `io.eventdriven.distributedprocesses`.

### U3. Kafka creates unknown topics on publish

**Where:** `e03/sagas_kafka/core/KafkaMessageBus.java:61-65` sends to whatever topic the route names. `KafkaConfiguration.java:35-46` declares only the configured topic and its `-dlt`. `W/docker-compose.yml:75-108` and `e03/sagas_kafka/core/KafkaTestConfiguration.java:18-19` keep broker defaults.

**What happens:** The broker default is `auto.create.topics.enable=true`, `num.partitions=1`, `default.replication.factor=1`. A route to an undeclared topic succeeds. The broker creates a topic that no group reads, the outbox checkpoint moves on, and the message is lost for the process. RabbitMQ catches the same mistake through `mandatory` and returns.

**Evidence:** https://kafka.apache.org/42/configuration/broker-configs/ . Spring Cloud Stream Kafka binder `autoCreateTopics`: with `false`, "if the topics do not exist, the binder fails to start". https://docs.spring.io/spring-cloud-stream/reference/kafka/kafka-binder/config-options.html

**Change:** Declare a `NewTopic` for every channel topic and every queue topic. Set `KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'` in Compose and on the Testcontainers broker.

### U4. RabbitMQ dead-lettering from a classic queue is at-most-once

**Where:** `e03/sagas_rabbitmq/core/RabbitConfig.java:67-72` declares a durable classic queue with `x-dead-letter-exchange`. Lines 81-95 declare a fanout DLX and one DLQ. Lines 118-133 wrap `RejectAndDontRequeueRecoverer`.

**What happens:** The broker republishes a dead letter from a classic queue without publisher confirms, after it drops the message from the source queue. If the target cannot take the message, the message is lost. A missing DLX drops it silently. A classic queue has no delivery limit, so a message that is requeued outside the retry interceptor can loop forever.

**Evidence:** RabbitMQ DLX: "messages can be lost if the target queue is not available to accept messages"; a missing DLX: "the messages are silently dropped"; "Quorum queues support at-least-once dead-lettering". https://www.rabbitmq.com/docs/dlx . Quorum queues: "should be considered the default choice when needing a replicated, highly available queue"; the delivery limit is 20 by default since 4.0; at-least-once needs `dead-letter-strategy=at-least-once` and `overflow=reject-publish`. https://www.rabbitmq.com/docs/quorum-queues . `QueueBuilder.quorum()`, `deliveryLimit(int)` and `Overflow.rejectPublish` exist in spring-amqp 4.1.2 (javap). Spring Cloud Stream RabbitMQ binder `quorum.enabled`: https://docs.spring.io/spring-cloud-stream/reference/rabbit/rabbit_overview/rabbitmq-consumer-properties.html . Alternative: `RepublishMessageRecoverer` adds "the exception message, original exchange, and routing key": https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html#async-listeners . `RepublishMessageRecovererWithConfirms` exists in spring-rabbit 4.1.2.

**Change:** Declare consumer queues as quorum queues with `x-dead-letter-strategy=at-least-once` and `overflow(rejectPublish)`, and keep the default delivery limit. This works on a single node, without replication. Or use `RepublishMessageRecovererWithConfirms`.

## Not idiomatic (should change)

### N1. Kafka publish timeout is shorter than the producer's own timeouts

**Where:** `props:16` (`publish-timeout=10s`), `KafkaMessageBus.java:65`.

**What happens:** The producer keeps retrying for up to `delivery.timeout.ms` (120 s), so it can still write the record after the bus gave up at 10 s. The outbox then publishes the same message again. Also, `send()` can block up to `max.block.ms` (60 s) for metadata before the 10 s wait starts. Idempotent handlers make the duplicate harmless, but the timeout does not mean what it says.

**Evidence:** `delivery.timeout.ms`, `max.block.ms`, and `retries` ("prefer to leave this config unset and instead use `delivery.timeout.ms`"): https://kafka.apache.org/42/configuration/producer-configs/ . Spring Kafka recommends `get()` with a timeout: https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html#examples

**Change:** Set `spring.kafka.producer.properties.delivery.timeout.ms` and `max.block.ms` so their sum fits in the publish timeout (`delivery.timeout.ms` must be at least `request.timeout.ms + linger.ms`). Keep `get(timeout)` a little longer.

### N2. The dead-letter `KafkaTemplate` has no observation registry

**Where:** `KafkaConfiguration.java:84-89` uses `new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(...))` and `setObservationEnabled(true)`.

**What happens:** `KafkaTemplate` looks up the `ObservationRegistry` only in `afterSingletonsInstantiated()` with an application context. A template that is not a bean keeps `ObservationRegistry.NOOP`, so the dead-letter publish has no span. That contradicts `e03/README.md:69` ("see retries and the dead-letter publish"). Nothing closes the producer factory at shutdown.

**Evidence:** The docs (https://docs.spring.io/spring-kafka/reference/kafka/micrometer.html#observation) do not say how the registry is found. javap on spring-kafka 4.1.2 shows the field starts as `NOOP` and changes only in `afterSingletonsInstantiated()` (needs `applicationContext != null`) or `setObservationRegistry`.

**Change:** Call `setObservationRegistry(observationRegistry)`, or declare the template and its factory as beans.

### N3. `messageId` is a new random UUID on each publish

**Where:** `e03/sagas_kafka/core/messages/MessageMetadata.java:28`, `e03/sagas_rabbitmq/core/messages/MessageMetadata.java:28`. It becomes the next `causationId` (`KafkaMessageBaggageHandler.java:30-35`, `RabbitMessageBaggageHandler.java:30-35`).

**What happens:** With the event store as outbox, a redelivered event gets a new `messageId`. Consumers cannot tell that two copies are the same, and each copy gives the next step a different `causationId`.

**Evidence:** Spring AMQP: "Stateful retry needs a mechanism to uniquely identify a message. The simplest approach is to have the sender put a unique value in the `MessageId` message property." https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html#retry . `samples/distributed-processes/README.md:629`: "each event reaches each subscriber at least once, and maybe more than once".

**Change:** Derive `messageId` from the stored event (id or position). On RabbitMQ, use the AMQP `message-id` property.

### N4. Routing keys and Kafka record keys by convention

**Where:** `e03/core/MessageRouter.java:37-52` (`firstRecordComponent` by reflection), used as the Kafka key at `KafkaMessageBus.java:52-54`. `RabbitMQMessageBus.java:51-54` uses the class name as the routing key.

**What happens:** The Kafka key decides the partition, and so the order of an entity's messages. With reflection, reordering record fields changes the partition silently. In the target, `routeTarget(...)` gives a `null` key, which on Kafka spreads one order's messages over all partitions. On RabbitMQ a `null` key is safe: `RabbitTemplate.nullSafeRoutingKey` turns it into `""`, which `#` matches.

**Evidence:** Ordering is kept per partition: https://kafka.apache.org/42/configuration/producer-configs/ . Topic key rules: https://www.rabbitmq.com/tutorials/tutorial-five-java

**Change:** Do not carry `firstRecordComponent` into the sample. Set the Kafka key from the route key, or from the entity id when the route has none.

### N5. One dead-letter destination per channel, not per consumer queue

**Where:** `KafkaConfiguration.java:40-46` (`<topic>-dlt`), `RabbitConfig.java:81-95` (fanout `<exchange>-dlx` to one DLQ).

**What happens:** In the target, one channel has several queues. On Kafka, all groups write failures to one `<topic>-dlt`, and a replay into the source topic reaches every group, not only the one that failed. On RabbitMQ, a fanout DLX copies each dead letter to every DLQ bound to it.

**Evidence:** Spring Cloud Stream Kafka binder default DLQ `error.<destination>.<group>`: https://docs.spring.io/spring-cloud-stream/reference/kafka/kafka-binder/config-options.html . RabbitMQ binder `autoBindDlq` per consumer queue: https://docs.spring.io/spring-cloud-stream/reference/rabbit/rabbit_overview/rabbitmq-consumer-properties.html . The Spring Kafka DLT "must have at least as many partitions as the original topic" with the default resolver: https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#dead-letters

**Change:** Use `<queue>-dlt` through a destination resolver on Kafka, and `<queue>-dlq` through a direct DLX with `x-dead-letter-routing-key` on RabbitMQ.

### N6. `mandatory=true` turns "no subscriber" into a publish failure

**Where:** `props:20`, `RabbitMQMessageBus.java:129-131`.

**What happens:** The target `MessageBus` says an event may have "Any number of subscribers ... or none" (`samples/distributed-processes/src/main/java/io/eventdriven/distributedprocesses/core/messaging/MessageBus.java:13-14`). On RabbitMQ, an unroutable event is returned and the bus throws. The forwarder keeps its checkpoint and fails on that event at every later append, so it stops there.

**Evidence:** https://docs.spring.io/spring-amqp/reference/amqp/template.html#template-confirms (`mandatory`, returns, `mandatory-expression`).

**Change:** Decide per channel. Keep `mandatory` on `requests` and `internal`. On `external` and `responses`, either make sure a queue always binds, or publish without `mandatory`. A `mandatory-expression` can choose per message.

### N7. RabbitMQ publishers share the connection with consumers

**Where:** `props:18-22`. Boot has no property for this, and the workshop does not set it.

**What happens:** In the target, a listener thread appends to the store, and the forwarder publishes in the same thread. A memory or disk alarm blocks publishing connections. If consumers use the same connection, they stop too, and the queues do not drain.

**Evidence:** "set the `usePublisherConnection` property to `true` ... to avoid consumers being blocked when a producer is blocked" https://docs.spring.io/spring-amqp/reference/amqp/template.html#separate-connection . "it is advisable to only use individual connections for either producing or consuming" https://www.rabbitmq.com/docs/alarms

**Change:** Add a `RabbitTemplateCustomizer` that calls `setUsePublisherConnection(true)`.

### N8. Two identical copies of the versioning code, and a stray fallback

**Where:** The five files in `e03/sagas_kafka/core/versioning/` are identical to `e03/sagas_rabbitmq/core/versioning/` apart from the package (checked with `diff`). `e03/sagas_rabbitmq/core/VersionedMessageConverter.java:39-45` falls back to an `eventType` header that nothing writes.

**Change:** Keep one broker-neutral versioning package in the sample, with a thin converter per broker. Drop the `eventType` fallback.

## Fine (keep)

- **F1. Kafka producer durability.** No overrides, so kafka-clients 4.2.2 defaults apply: `acks=all`, `enable.idempotence=true`, `retries=MAX`, `max.in.flight=5`. Order is kept per partition, and producer retries write no duplicates (https://kafka.apache.org/42/configuration/producer-configs/). `replicas(1)` (`KafkaConfiguration.java:37,42`) is fine locally. For production, the "typical scenario" is RF 3, `min.insync.replicas=2`, `acks=all` (https://kafka.apache.org/42/configuration/topic-configs/).
- **F2. Kafka sync send.** `KafkaMessageBus.java:64-78` matches the Spring Kafka "Blocking (Sync)" example, and it keeps the interrupt flag (`KafkaPublishFailureTest.java:53-66`).
- **F3. Kafka offset commits.** Spring sets `enable.auto.commit=false`, and the default AckMode is `BATCH` (https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html#committing-offsets). If the recoverer fails, the record is sought again (https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#default-eh). The result is at-least-once.
- **F4. Kafka deserialization.** `ByteArrayDeserializer` (`props:9`) plus conversion in the listener (`KafkaMessageBus.java:84`, `VersionedMessageConverter.java:25-38`) means nothing can fail inside `poll()`, so `ErrorHandlingDeserializer` is not needed. Read errors reach `DefaultErrorHandler`, and the DLT gets the raw bytes (`KafkaErrorHandlingTest.java:127-159`). Source: https://docs.spring.io/spring-kafka/reference/kafka/serdes.html#error-handling-deserializer
- **F5. Kafka retries and DLT.** `KafkaConfiguration.java:53-82` uses built-in `DefaultErrorHandler`, `DeadLetterPublishingRecoverer` and `setBackOffFunction`. The `-dlt` name matches the 4.1 default (also checked in the jar). `setClassifications(Map.of(), true)` (line 75) removes the default fatal list, which is acceptable because `MessageFailureStrategy` decides. The delivery-attempt header (lines 100-104) and `RetryBackOff` (`e03/core/RetryBackOff.java:10-15`) are fine. The blocking retries are documented in `e03/README.md:47`.
- **F6. RabbitMQ retries.** Boot stateless retry (`props:23-26`), `RabbitListenerRetrySettingsCustomizer` (`RabbitConfig.java:102-116`), and the wrapped recoverer (lines 118-133) are the documented hooks (https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html#retry). The converter is set only on the template (lines 32-42), so read errors happen inside the retry interceptor.
- **F7. RabbitMQ confirms and returns.** `props:18-20` and `RabbitMQMessageBus.java:62-74,112-132` wait on `CorrelationData` with a timeout, check `ack()`, then check `getReturned()`. Spring AMQP guarantees "that the returned message property is set before the future is set with the `ack`" (https://docs.spring.io/spring-amqp/reference/amqp/template.html#template-confirms).
- **F8. RabbitMQ persistence and topology.** The default delivery mode is `PERSISTENT` (javap). `new TopicExchange` is durable, and the queues are `QueueBuilder.durable`. Bean declarations are redeclared "whenever a connection is established (initially and after a connection failure)" (https://docs.spring.io/spring-amqp/reference/amqp/broker-configuration.html#collection-declaration). Kafka `NewTopic` beans are created by `KafkaAdmin` (`spring.kafka.admin.auto-create` defaults to `true` in the Boot 4.1.2 metadata).
- **F9. RabbitMQ ack mode, prefetch, order.** The defaults apply: `AUTO` ack, prefetch 250 (javap), one consumer. Order holds per queue with one consumer only (https://www.rabbitmq.com/docs/semantics). That is fine because the sample's handlers do not assume an order. `singleActiveConsumer()` exists if order is needed later.
- **F10. Observation and baggage.** The Boot `observation-enabled` properties (`props:11-12,21-22`) are the documented switch. `remote-fields` puts each field on the wire under its own name (https://docs.spring.io/spring-boot/reference/actuator/tracing.html#actuator.micrometer-tracing.baggage). The manual `correlationId` header (`MessageMetadata.java:24-26`) matters only for the first message. Later, the propagator overwrites it: javap shows `KafkaRecordSenderContext` removes the header before it adds the new one, and `RabbitMessageSenderContext` uses `setHeader`. The causation `ObservationHandler` classes are the Micrometer extension point, and Spring has nothing built in for this.
- **F11. Versioning.** Spring has type-name mapping but no upcaster, so `MessageTransformations` does not duplicate a Spring feature. Keep it, with stable names (U2) and one copy (N8).
- **F12. Compose and Tempo.** `W/docker-compose.yml:58-108` and `W/observability/tempo-config.yaml` are fine as local settings, with the U3 change.

## Mapping the target bus onto the brokers

**RabbitMQ (one-to-one, idiomatic):** channel → durable topic exchange; queue → durable quorum queue (U4); `bind(queue, channel, pattern)` → binding with the same pattern (`*` one word, `#` zero or more words, keys up to 255 bytes: https://www.rabbitmq.com/tutorials/tutorial-five-java); `subscribe` → one listener container per queue, which dispatches by `__TypeId__`. Spring Cloud Stream's RabbitMQ binder uses the same shape: a topic exchange per destination, a queue per group, and `#` as the default key. Runtime `bind()` declarations "will not be recovered" after a lost connection (https://docs.spring.io/spring-amqp/reference/amqp/broker-configuration.html#declarable-recovery). Use `Declarables` beans or `RabbitAdmin.setRedeclareManualDeclarations(true)` (present in 4.1.2). Register containers per queue with `RabbitListenerConfigurer` and `SimpleRabbitListenerEndpoint` (https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven/registration.html).

**Kafka (works, with one extra hop):** channel → declared topic (U3); queue bound with `#` → a consumer group named after the queue that reads the channel topic; queue with a narrower pattern → the routing consumer matches the key and writes to the queue's topic. Per-record topic routing is a Kafka Streams feature (`TopicNameExtractor`: https://kafka.apache.org/42/javadoc/org/apache/kafka/streams/processor/TopicNameExtractor.html), so this is a known shape. The routing consumer must send synchronously before the commit (F3), keep the record key and all headers (type, trace, baggage, `messageId`), and write to topics with at least as many partitions as the channel (N5). Create containers per queue with `factory.createContainer(topic)`, `setGroupId`, and `start()` (https://docs.spring.io/spring-kafka/reference/kafka/dynamic-containers.html), and stop them when the context closes.

## What I could not verify

- Whether `confluentinc/confluent-local:8.4.1` changes `auto.create.topics.enable`. I used the Apache Kafka default (`true`).
- The Spring Boot 4.1 properties page was too long to fetch. I read defaults from `spring-configuration-metadata.json` in the Boot 4.1.2 jars instead.
- I did not run the tests. N2 and F10 rest on docs and class files, not on a test run.
