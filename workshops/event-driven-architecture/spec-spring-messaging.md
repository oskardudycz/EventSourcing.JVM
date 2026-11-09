# Spec: Spring Boot 4, messaging bug fixes, and idiomatic Spring messaging exercises

Workshop: `workshops/event-driven-architecture` (both `solved` and `exercises`).

Path shorthand used below:
- `S/` = `solved/src/test/java/io/eventdriven/eventdrivenarchitecture/`
- `X/` = `exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/`

## Why

The Kafka and RabbitMQ variants of the e03 sagas (`S/e03_businessprocesses/sagas_kafka`, `S/e03_businessprocesses/sagas_rabbitmq`) work, but they aren't how Spring developers write messaging code, and they contain real bugs. They bolt the broker under a hand-rolled bus: custom dispatch, swallowed errors, hand-built templates and container factories, metadata passed through tracer baggage. Attendees never see Spring Kafka or Spring AMQP as they'd meet them at work.

We keep the `MessageBus` abstraction in e03 because it gives fast, deterministic tests and lets one scenario suite run against several transports. Around it we add focused exercises that show the native Spring ways, each fully idiomatic and each running on a real broker.

## Decisions

1. **Order of work:** bump to Spring Boot 4 first, then fix bugs, then make the e03 broker adapters idiomatic, then add new exercises. Each step leaves all tests green.
2. **e03 keeps `MessageBus`** with three implementations: in-memory, Kafka, RabbitMQ. The broker-backed ones become thin adapters that lean on Spring Kafka and Spring AMQP (the "middle ground").
3. **One scenario suite per domain flavour** runs unchanged against all three e03 buses. The duplicated domain code in `sagas_kafka` and `sagas_rabbitmq` goes away.
4. **Bugs are fixed test-first** before the adapters are reworked, so the bug tests act as regression tests for the rework.
5. **New exercises:**
   - `e04_spring_kafka` and `e04_spring_rabbitmq`: native Spring Kafka and Spring AMQP, no `MessageBus`.
   - `e05_spring_cloud_stream`: one exercise, the same code run on the Kafka binder and the RabbitMQ binder.
   - `e06_spring_modulith`: one exercise, Kafka only, about the dual-write problem and the outbox.
6. **Every exercise runs on a real broker** (Testcontainers). In-memory test helpers (Spring Cloud Stream test binder, Modulith `Scenario`) may appear as side notes, never as the main suite.
7. **Each exercise reuses the e03 saga as a pure function** (`GroupCheckoutSaga.handle(event)`). Only the wiring changes between exercises.
8. **Kafka publishing in the e03 adapter stays synchronous**, with a bounded timeout. The facade stores state and then publishes; it must learn when the publish fails. (This corrects the earlier review, which flagged the blocking `get()` as non-idiomatic. Blocking is fine here; the unbounded wait and the exception wrapping are what change.)
9. **"Cloud contracts"** in the discussion meant Spring Cloud Stream. Spring Cloud Contract is out of scope.

## Answered questions

| # | Question | Answer |
|---|---|---|
| Q1 | Spring Boot 4.1.x or 4.0.x? The latest Spring Cloud GA train (2025.1.x, Stream 5.0.x) targets Boot 4.0; the train for Boot 4.1 (Stream 5.1) is at RC2. | Boot 4.1.2. Do e05 last, once the Spring Cloud train for Boot 4.1 is GA. |
| Q2 | Java version? `solved` uses 22, `exercises` 21, CI 22. | No change. Boot 4 runs on both. |
| Q3 | Jackson 3, or Jackson 2 via Boot 4's deprecated compatibility support? | Jackson 3 in the bump. |
| Q4 | Remove dependencies with zero usages (springdoc, spring-retry, EventStoreDB client, MongoDB driver, Testcontainers MongoDB)? | Remove them in the bump. |
| Q5 | Which domain flavour do e04–e06 use? | Both: aggregates (`solution1_aggregates`) and deciders (`solution2_immutableentities`), as in e03. |
| Q6 | Do attendees design the topology (topics, ownership, keys; exchanges, queues, routing keys)? | Yes. |
| Q7a | Correlation/causation headers in e04? | Left out. Candidate for a dedicated observability exercise later. |
| Q7b | Name the dual write in e04? | Yes, in the solutions only: at the store-then-send point in the solved facades. Not in the exercise README. |
| Q7c | JSON serialization in e04? | Given to attendees. |
| Q8 | How does e06 persist entities, given that the outbox needs real transactions? | Spring Data JPA on Postgres. |

## Conventions for all steps

- TDD: failing test, minimal code, green, refactor.
- Test output must be clean. Logs that are supposed to contain errors are captured and asserted.
- Conventional commits, imperative mood, focused on why. Never `--no-verify`.
- One branch per step, off `main`, merged before the next step starts.
- Exercise tests in `exercises` carry `@Tag("Exercise")`, so CI (`-Pci=true`) skips them, as it does today.
- Run tests with `docker compose up -d` in the project folder, as CI does. Kafka and RabbitMQ come from Testcontainers.

---

## Step 0: Baseline

Goal: know what "green" means before changing anything.

1. In `solved` and `exercises`: `docker compose up -d`, then `./gradlew test` and `./gradlew build -Pci=true`.
2. Record per project: tests run, passed, failed, skipped, and any error or warning noise in the output.
3. Known before starting: `S/e03_businessprocesses/sagas_rabbitmq/core/MessageMetadataPropagationTest` is `@Disabled("TODO: Check propagation")`. Exercise tests in `exercises` fail locally by design.

Done when: the baseline numbers are written into the PR description of step 1.

## Step 1: Bump to Spring Boot 4

Goal: both projects on Spring Boot 4 with the same test results as the baseline. No behaviour changes.

Tasks, in both `solved/build.gradle` and `exercises/build.gradle` unless noted:

1. Set plugin `org.springframework.boot` to 4.1.2 (Q1) and `io.spring.dependency-management` to 1.1.7.
2. Drop explicit versions from every dependency the Boot BOM manages: starters, Jackson, Log4j, Micrometer, OpenTelemetry, PostgreSQL driver, AssertJ, Testcontainers. Keep versions only for what the BOM doesn't manage (`net.datafaker:datafaker`).
3. Remove unused dependencies (Q4): `springdoc-openapi-starter-webmvc-ui`, `spring-retry`, `com.eventstore:db-client-java`, `org.mongodb:mongodb-driver-sync`, `org.testcontainers:mongodb`.
4. `solved` only: replace `org.springframework.kafka:spring-kafka` with `org.springframework.boot:spring-boot-starter-kafka`. Boot 4 moved auto-configuration into per-technology modules, so the bare library no longer brings Kafka auto-configuration.
5. Testcontainers 2: switch to the renamed artifacts (`testcontainers-kafka`, `testcontainers-rabbitmq`, `testcontainers-junit-jupiter`). In `S/e03_businessprocesses/sagas_kafka/core/KafkaTestConfiguration.java`, replace the deprecated `org.testcontainers.containers.KafkaContainer` with `org.testcontainers.kafka.ConfluentKafkaContainer`, and update `RabbitMQContainer` to its Testcontainers 2 package.
6. Jackson 3 (Q3): move `com.fasterxml.jackson.databind`/`core` imports to `tools.jackson.*` (annotations stay in `com.fasterxml.jackson.annotation`), drop `jackson-datatype-jsr310` (built into Jackson 3), and switch Spring Kafka and Spring AMQP to their Jackson 3 serializers, deserializers, type mappers and message converters. Take the exact class names from the Spring Kafka 4 and Spring AMQP 4 reference docs.
7. Fix imports that moved with Boot 4's modularization. Expect at least `KafkaProperties`, `KafkaConnectionDetails`, `DefaultKafkaProducerFactoryCustomizer`, `@ServiceConnection` and `@AutoConfigureObservability`. Follow the Spring Boot 4 migration guide; don't guess.
8. Java versions stay as they are (Q2).
9. Keep `application.properties` working. Rename any property Boot 4 renamed; the startup log reports them.

Done when:
- Both projects build with `-Pci=true`, and test counts match the baseline.
- No new warnings or deprecation notices in the build or test output that come from our own code.
- The commit message explains the why: Boot 3.4 support is ending, and the new exercises need Spring Modulith 2.1 (Boot 4).

## Step 2: Fix bugs in the e03 Kafka and RabbitMQ variants

Goal: the broker-backed e03 variants behave correctly. Each bug gets a failing test first, then the smallest fix. Structure and style stay as they are; step 3 reshapes them.

| # | Bug | Test that proves it | Fix |
|---|---|---|---|
| B1 | The RabbitMQ variant depends on Kafka. `sagas_rabbitmq/core/RabbitMessageBaggageHandler.java` imports `sagas_kafka...MessageMetadata` and Kafka's `ProducerRecord`. `sagas_rabbitmq/core/messages/MessageMetadata.java` uses Kafka headers and `ConsumerRecord`. `sagas_rabbitmq/core/versioning/MessageSerializationConfiguration.java` is a copy of the Kafka one and registers a Kafka producer customizer. | An ArchUnit test (`com.tngtech.archunit:archunit-junit5`, test scope): nothing in `..sagas_rabbitmq..` depends on `org.apache.kafka..`, `org.springframework.kafka..` or `..sagas_kafka..`. Add the mirror rule for `sagas_kafka` and AMQP. | Point imports to the RabbitMQ module's own classes. Remove Kafka types from the RabbitMQ `MessageMetadata`. Replace the copied serialization configuration with RabbitMQ-only beans. |
| B2 | The RabbitMQ consumer never puts `correlationId` into baggage. `onScopeOpened` maps `CAUSATION_ID` to itself where the Kafka handler maps `CORRELATION_ID`. | Remove `@Disabled` from `sagas_rabbitmq/core/MessageMetadataPropagationTest`. It fails on the correlation assertion. | Map `CORRELATION_ID` as the Kafka handler does. |
| B3 | `causationId` gets the wrong value. Both `setHeadersFromBaggage` methods fill `CAUSATION_ID` from `entityId` baggage, not from the ID of the message being handled. | Strengthen both propagation tests: the `causationId` of `GuestCheckedOut` must equal the `messageId` of the `CheckOutGuest` command that triggered it. Today the test only checks that it isn't the initiating message's ID. | Write `CAUSATION_ID` from the causation baggage (the incoming `messageId`). |
| B4 | Handler exceptions are swallowed. Both listeners catch everything and print it, so the offset is committed or the message acked, and the message is lost. | Per broker: subscribe a handler that throws for one message. Assert the message is retried, then ends up on a dead-letter destination (`<topic>.DLT` for Kafka; a DLQ bound to a dead-letter exchange for RabbitMQ), and that the next message is still processed. | Stop catching in the listener. Kafka: a `DefaultErrorHandler` bean with `DeadLetterPublishingRecoverer` and a bounded `FixedBackOff`. RabbitMQ: `spring.rabbitmq.listener.simple.retry.*` properties, a `RejectAndDontRequeueRecoverer`, and the saga queue declared with `x-dead-letter-exchange`. |
| B5 | Deserialization failures are swallowed. An unreadable payload or unknown type is printed and dropped. | Per broker: publish a raw message with an unknown type header and one with broken JSON. Both reach the dead-letter destination without retries, and the next message is processed. | Throw from deserialization so B4's error handling applies, and mark deserialization exceptions as not retryable (Kafka: `addNotRetryableExceptions`). |

Done when:
- All new tests pass on both brokers, the RabbitMQ propagation test runs (no `@Disabled`), and everything from step 1 is still green.
- Logs from deliberate failures are asserted, not left as noise.

## Step 3: Make the e03 broker adapters idiomatic

Goal: Kafka and RabbitMQ implementations of `MessageBus` that read like Spring code, and one scenario suite run against all three buses. The step 2 tests stay green throughout.

### 3a. One scenario suite for all transports

1. Make `S/e03_businessprocesses/sagas/solution1_aggregates/BusinessProcessTests.java` and its `solution2` counterpart transport-agnostic. Assertions wait with Awaitility, which passes immediately for the in-memory bus.
2. The Kafka and RabbitMQ runs reuse those scenarios (for example, a subclass per transport that supplies the Spring context and the bus) instead of keeping copies.
3. Delete the duplicated domain code in `sagas_kafka/solution*` and `sagas_rabbitmq/solution*`. Today it's identical to `sagas/solution*` apart from Spring annotations on the facades. What remains in each broker package is the adapter, its configuration, and the per-transport test wiring.

Done when: each scenario exists in one source file and runs three times (in-memory, Kafka, RabbitMQ), all green.

### 3b. Publishing

1. Routing: each message gets a target (Kafka topic, RabbitMQ exchange) and a key (Kafka record key, RabbitMQ routing key). Both follow a convention, so no message type needs a registration. Kafka uses the configured topic and the first record component as the key, so messages for one entity stay in order on one partition when the entity ID comes first. RabbitMQ uses the configured exchange and the message class name. `MessageRoutes` beans can override either value per class, per supertype or per package (`inPackage`). Target and key resolve independently: the first matching registration that provides one wins, and anything missing, or resolved to `null`, comes from the convention. Tests check the key on a raw consumed Kafka record and the routing key on a bound RabbitMQ queue.
2. Kafka: wait for the broker's acknowledgement with a bounded timeout (decision 8). Don't wrap the exception in `RuntimeException`; restore the interrupt flag on `InterruptedException`.
3. RabbitMQ: turn on publisher confirms (`spring.rabbitmq.publisher-confirm-type=correlated`) and wait for the confirm with a bounded timeout. Routing key = message type name. Remove the hard-coded `groupcheckout` segment in `buildRoutingKey`.
4. Remove the manual `messagebus.publish` and `messagebus.handle` spans. Template and listener observations already trace both.

### 3c. Receiving

1. Kafka: move message versioning (`TransformingMessageDeserializer`) into a `Deserializer<Object>` that tries the registered transformations first, then delegates to the Jackson deserializer, wrapped in `ErrorHandlingDeserializer`. The consumer value deserializer stops being `StringDeserializer`. The listener receives the typed payload.
2. RabbitMQ: the same, as a `MessageConverter` that delegates to the Jackson converter. The listener method takes the payload, not a raw `Message`.
3. A single generic listener per adapter that dispatches to subscribers stays. That's the adapter's job.
4. Handler registry: `ConcurrentHashMap`, since subscriptions and the listener thread both touch it.

### 3d. Configuration

1. Delete the custom `KafkaTemplate`, `ConcurrentKafkaListenerContainerFactory`, `RabbitTemplate` and `SimpleRabbitListenerContainerFactory` beans. Turn on observation with `spring.kafka.template.observation-enabled`, `spring.kafka.listener.observation-enabled`, `spring.rabbitmq.template.observation-enabled` and `spring.rabbitmq.listener.simple.observation-enabled`. For RabbitMQ, declare only the `MessageConverter` bean; Boot attaches it to the template and the listener factory.
2. RabbitMQ topology declared once, as `Queue`/`TopicExchange`/`Binding` beans. The listener uses `@RabbitListener(queues = ...)`; drop the duplicate `@QueueBinding`.
3. Constructor injection only. Replace the repeated `@Value("${app.kafka.topic:hotel-financial}")` and `${app.rabbitmq.exchange:...}` defaults with `@ConfigurationProperties` records bound to `app.kafka` and `app.rabbitmq`.
4. Set the Kafka value serializer in one place. Today `application.properties` says `StringSerializer` and a customizer replaces it via a raw cast.
5. `System.out`/`System.err`/`printStackTrace` replaced by SLF4J loggers.

### 3e. Message metadata

1. Messages carry `messageId`, `correlationId` and `causationId` as broker headers.
2. Find out, through the step 2 propagation tests, how much Boot's `management.tracing.baggage.remote-fields` already propagates. Keep custom code only for what it doesn't do (expected: mapping the incoming `messageId` to `causationId`).
3. `MessageMetadata`: `final` class with a private constructor instead of `abstract`. Broker-specific helpers live in the broker's own package.

### 3f. Test cleanup

1. Delete `S/e03_businessprocesses/sagas_kafka/core/KafkaPropertiesBootstrapTest.java`, a debugging leftover.
2. Replace `@ComponentScan` on test classes with a small `@SpringBootConfiguration` per variant. Today the scan only works because `@Import` on the same class turns the test into configuration.

### 3g. Java naming

1. `SagaResult.Send`, `Publish` and `Ignore` become `send`, `publish` and `IGNORE`, in every e03 copy, including `exercises` if it has them.

Done when:
- Step 2 tests and the 3a scenario suite are green on all transports.
- No catch-all in listeners, no `System.out`, no field injection, no hand-built templates or container factories.
- `MessageMetadataPropagationTest` passes for both brokers.

## Step 4: `e04_spring_kafka`

Goal: attendees wire the e03 group checkout with native Spring Kafka, without `MessageBus`.

Location: `S/e04_spring_kafka/` (solution) and `X/e04_spring_kafka/` (exercise), plus `X/e04_spring_kafka/README.md`. Both domain flavours (Q5), as in e03: `solution1_aggregates/` and `solution2_immutableentities/`, sharing a `core/` package for test infrastructure. Build the solution first, test-first, then derive the exercise from it by removing the wiring and leaving TODOs.

### Topology in the solution

| Topic | Owner | Content | Key |
|---|---|---|---|
| `guest-stay-account-events` | guest stay accounts | `GuestStayAccountEvent` | guest stay account ID |
| `guest-stay-account-commands` | guest stay accounts | `CheckOutGuest` (sent by the saga) | guest stay account ID |
| `group-checkout-events` | group checkouts | `GroupCheckoutEvent` | group checkout ID |
| `group-checkout-commands` | group checkouts | `RecordGuestCheckoutCompletion`, `RecordGuestCheckoutFailure` | group checkout ID |

Rule shown in the README: events go on the publisher's topic, commands on the receiver's. Topics are declared as `NewTopic` beans with 3 partitions. Each module has its own consumer group (`guest-stay-accounts`, `group-checkouts`). Failed records go to `<topic>.DLT`.

### What attendees get

1. The e03 domain in both flavours (Q5): entities or deciders, events, commands, and the pure `GroupCheckoutSaga.handle(...)`.
2. Facades with storage logic done and a TODO where events must be sent.
3. JSON serialization configured, including type mapping and trusted packages (Q7c).
4. Testcontainers setup with `@ServiceConnection`, and a test-only listener on all topics that collects messages into a `MessageCatcher`.
5. `BusinessProcessTests` with the e03 scenarios, made asynchronous with Awaitility, tagged `@Tag("Exercise")`.
6. A dead-letter test: a poison record ends up on `<topic>.DLT`, and processing continues.

### What attendees build

1. The topology: `NewTopic` beans, which topic each message goes to, record keys (Q6).
2. Sending from the facades with `KafkaTemplate`.
3. A saga listener: class-level `@KafkaListener` on `guest-stay-account-events` and `group-checkout-events`, one `@KafkaHandler` per event type. It calls the saga function and sends the resulting commands.
4. Command listeners that call the facades.
5. Error handling: a `DefaultErrorHandler` bean with `DeadLetterPublishingRecoverer` and a bounded backoff.

Correlation and causation headers are out of scope (Q7a).

The solved facades name the dual write (Q7b) with a short comment where they store state and then send: if the send fails or the process dies in between, the state is saved but no event goes out, and the group checkout never continues. The comment points to e06. The exercise README doesn't mention it.

Done when: the solution is green in `solved`; the exercise compiles in `exercises` and its tests fail only at the TODOs; CI is green in both projects.

## Step 5: `e04_spring_rabbitmq`

Goal: the same as step 4, with native Spring AMQP. Same layout, same "get" list, same scenarios.

### Topology in the solution

| Exchange | Type | Owner | Routing key | Bound queues |
|---|---|---|---|---|
| `guest-stay-accounts.events` | topic | guest stay accounts | event type name | `group-checkouts.guest-stay-account-events` |
| `guest-stay-accounts.commands` | direct | guest stay accounts | command type name | `guest-stay-accounts.commands` |
| `group-checkouts.events` | topic | group checkouts | event type name | `group-checkouts.group-checkout-events` |
| `group-checkouts.commands` | direct | group checkouts | command type name | `group-checkouts.commands` |

Every queue has `x-dead-letter-exchange` pointing to a `dlx` exchange with a DLQ per queue. The same ownership rule as e04 Kafka applies, expressed through RabbitMQ's exchange/queue model, and the README contrasts it with Kafka's.

### What attendees build

1. Exchanges, queues and bindings as beans (Q6).
2. Sending with `RabbitTemplate.convertAndSend(exchange, routingKey, message)`.
3. A saga listener: class-level `@RabbitListener(queues = ...)`, one `@RabbitHandler` per event type, calling the saga function.
4. Command listeners calling the facades.
5. Retry and dead-lettering through `spring.rabbitmq.listener.simple.retry.*` and a `RejectAndDontRequeueRecoverer` bean.

Both domain flavours and the dual-write comment in the solved facades, as in step 4.

Done when: the same criteria as step 4.

## Step 6: `e05_spring_cloud_stream`

Goal: the same flow with Spring Cloud Stream's functional model, run unchanged on both brokers.

Prerequisite: the Spring Cloud release train matching the workshop's Boot version is GA (Q1).

1. Dependencies: the Spring Cloud BOM, `spring-cloud-stream-binder-kafka` and `spring-cloud-stream-binder-rabbit`.
2. The saga as functions, for example `Function<GuestStayAccountEvent, GroupCheckoutCommand>`. The fan-out from `GroupCheckoutInitiated` to several `CheckOutGuest` commands returns a list of messages; the binder sends each element separately (confirmed in the Spring Cloud Stream docs).
3. Command handlers as `Consumer<...>` beans that call the facades.
4. Facades send events with `StreamBridge.send(bindingName, binderName, event)`. With two binders on the classpath, `StreamBridge` needs the binder name.
5. Several message types share one destination, so the sealed `GuestStayAccountEvent` and `GroupCheckoutCommand` interfaces get Jackson polymorphic typing (`@JsonTypeInfo`/`@JsonSubTypes`). The README explains this trade-off.
6. Tests: two test classes run the same scenarios, one with a Kafka container and `spring.cloud.stream.default-binder=kafka`, the other with a RabbitMQ container and `default-binder=rabbit`.
7. Verify by test, not assumption, that a function returning `null` sends nothing. It's used for "the saga ignores this event".

What attendees get and build follows steps 4 and 5: domain (both flavours), facades, serialization and tests given; functions, bindings and error handling built. The README compares the result with e04: what got shorter, and what's now hidden.

Done when: both test classes are green in `solved`, and the exercise is derived as in step 4.

## Step 7: `e06_spring_modulith`

Goal: show the dual-write problem and its fix. The broker carries the flow between modules.

1. Dependencies: the Spring Modulith 2.1 BOM, `spring-modulith-starter-core`, `spring-modulith-starter-jpa` (event publication registry), `spring-modulith-events-kafka`, `spring-modulith-events-api`, `spring-boot-starter-data-jpa`. Kafka only; the AMQP externalizer isn't added. Both externalizers would be active together, and every event would go to both brokers.
2. Persistence (Q8): Spring Data JPA on Postgres, through Testcontainers with `@ServiceConnection`. Facades are `@Transactional` and use Spring Data repositories.
   - Aggregates flavour: the aggregates are JPA entities.
   - Deciders flavour: the state is immutable records, which JPA can't map as entities. A small JPA entity per stream holds the ID, a version for optimistic locking, and the state as a JSON column (`@JdbcTypeCode(SqlTypes.JSON)`). The decider code stays unchanged.
3. Guest stay accounts publish their events with `ApplicationEventPublisher` inside the transaction. Events are annotated `@Externalized("guest-stay-account-events::#{guestStayAccountId()}")`.
4. Group checkouts consume `guest-stay-account-events` with `@KafkaListener` as if they were a separate service, run the saga, and call their facade.
5. Two externalization modes, both in the README:
   - the default module listener, which the Modulith docs say "lacks critical features developers might expect from actual outbox pattern implementations";
   - a real outbox through Namastack Outbox or JobRunr (Modulith 2.1). Pick one while building; document why.
6. Tests:
   - the e03 scenarios, end to end, with Postgres and Kafka containers;
   - the failure case: the broker is unavailable when the transaction commits; the event stays in the registry or outbox and reaches Kafka once the broker is back;
   - `ApplicationModules.of(...).verify()` to check the module boundaries.

Done when: all tests are green in `solved`, and the exercise is derived as in step 4. In the exercise, attendees build the transactional publishing, `@Externalized`, the consuming listener and the failure test; the persistence code is given.

## Step 8: Documentation and CI

1. Workshop `README.md`: list e04–e06 after item 7, with one line each on what it teaches and that it needs Docker.
2. `X/e03_businessprocesses/README.md`: one line saying the saga function is what later exercises reuse, and only the wiring changes.
3. CI: both workflows already run `docker compose up -d` and `./gradlew build -Pci=true`. Confirm the Testcontainers-based tests run on GitHub's Ubuntu runners within a reasonable time. If not, split the broker-backed tests into a separate job.

Done when: CI is green for both projects on the final branch.

## Later, not in this spec

- An observability exercise: correlation and causation IDs across a process, traces through the broker, finding every message of one group checkout. Designed separately (Q7a).
