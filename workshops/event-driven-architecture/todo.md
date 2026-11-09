# Todo: Spring Boot 4, messaging bug fixes, Spring messaging exercises

Prompts live in [plan.md](plan.md); the spec is [spec-spring-messaging.md](spec-spring-messaging.md). Tick a box when its prompt is done and committed. Work in `solved` first; `exercises` is derived from it.

## Decisions to confirm

- [x] **D-e06** (before 7.1, confirmed by Oskar 2026-11-06): `CheckOutGuest` stays a command, sent as a message on `guest-stay-account-commands` through Modulith's outbox (`@Externalized`, entering through `ApplicationEventPublisher`, the outbox's only entry point). Every message between modules, events and commands alike, goes through the outbox. See plan.md section 1.

## Baseline (filled by 0.1)

Recorded 2026-11-06 on `main` at `75d89e7`, Boot 3.4.2.

Local setup, needed by every later prompt:
- `solved` needs a Java 22 toolchain, and this machine has only 21, 25 and 26. Temurin 22.0.2 is unpacked in `~/.jdks/jdk-22.0.2+9`. Run Gradle in `solved` with `-Dorg.gradle.java.installations.paths=$HOME/.jdks/jdk-22.0.2+9`. Use `-D`; the `-P` form is deprecated and adds a Gradle deprecation warning.
- `docker compose up -d` was skipped. EventSourcing.NetCore's containers already hold ports 5432, 1113 and 2113. No test uses Postgres, EventStoreDB or Mongo. Kafka and RabbitMQ come from Testcontainers.

### solved (`test` task)

| Run | Tests | Passed | Failed | Skipped | Time |
|---|---|---|---|---|---|
| `./gradlew clean test` | 117 | 116 | 0 | 1 | 1m 37s |
| `./gradlew build -Pci=true` | 117 | 116 | 0 | 1 | 1m 16s |

| Area | Tests |
|---|---|
| e01 | 2 |
| e02 solution1, solution2 | 7 + 7 |
| e03 choreography, processmanagers, todolist, transactions | 10 each |
| e03 sagas (in-memory) | 20 |
| e03 sagas_kafka | 20 |
| e03 sagas_rabbitmq | 21 (1 skipped) |

- Skipped: `sagas_rabbitmq/core/MessageMetadataPropagationTest` is `@Disabled`, as the spec says.
- There are no pre-existing failures.

### exercises (`test` task)

| Run | Tests | Passed | Failed | Skipped |
|---|---|---|---|---|
| `./gradlew clean test` | 29 | 12 | 17 | 0 |
| `./gradlew build -Pci=true` | 0 | 0 | 0 | 0 |

- Every test class in `exercises` is tagged `Exercise`, so CI runs no tests there. CI only checks that `exercises` compiles.
- The 17 failures are expected for unsolved exercises: e01 `EventsDefinitionTests` (2), e02 `EntityDefinitionTests` (7), and e03 `BusinessProcessTests` solution1 (4) and solution2 (4).

### Noise in the solved output (all from passing tests)

- `Configuration status="DEBUG"` in `solved/src/test/resources/log4j2.xml` makes log4j print about 60 lines of its own setup at DEBUG. The same file also configures a logger for `io.eventdriven.ecommerce`, a package this project doesn't have.
- `sagas_kafka/core/KafkaMessageBus` and `sagas_rabbitmq/core/RabbitMQMessageBus` print `WARNING: No handler found for message …` with `System.out` about 94 times. This happens for every event nobody subscribes to: `GuestCheckedIn`, `ChargeRecorded`, `PaymentRecorded` and the `GuestCheckout*Recorded` events.
- `sagas_kafka/core/MessageCatcher` and `sagas_rabbitmq/core/MessageCatcher` print `=== EXPECTED MESSAGES === / === ACTUAL MESSAGES ===` dumps with `System.out` whenever an Awaitility poll runs before the messages arrive. `BusinessProcessTests` and `MessageMetadataPropagationTest` trigger them.
- `sagas_kafka/core/KafkaPropertiesBootstrapTest` is a diagnostic test that prints producer properties and connection details with `System.out`.
- On STANDARD_ERROR, during `KafkaPropertiesBootstrapTest`: Mockito self-attaches its inline mock maker, which triggers the JDK's "Java agent has been loaded dynamically" warnings, plus `OpenJDK … Sharing is only supported for boot loader classes`.
- 7 Spring Boot banners, one per Spring context.
- The e02 and e03 `EntityDefinitionTests` use `System.out.println(exc.getMessage())` in a catch block. It printed nothing in this run.
- From Gradle, not our code: the native-access `WARNING: A restricted method in java.lang.System has been called`. Gradle runs on JDK 25 here. The build script raises no deprecation warnings when run with `--warning-mode all`.

### Noise in the exercises output

- Only Gradle's native-access warning, plus the assertion failures of the 17 expected failing tests.

## Step 0: Baseline

- [x] 0.1 Record the baseline

## Step 1: Spring Boot 4 (`chore/spring-boot-4`)

- [x] 1.1 Remove unused dependencies
- [x] 1.2 Let the Boot BOM manage versions (Boot 3.4)
- [x] 1.3 Bump solved to Boot 4.1.2 (Jackson 2 compatibility)
- [x] 1.4 Move solved to Jackson 3
- [x] 1.5 Derive the exercises build from solved
- [x] 1.6 Close step 1: PR, Oskar merges

## Step 2: e03 broker bugs (`fix/e03-broker-bugs`)

- [x] 2.1 ArchUnit guard for the Kafka variant
- [x] 2.2 B1: RabbitMQ variant without Kafka dependencies
- [x] 2.3 B2: RabbitMQ correlation ID propagation (done together with 2.4, agreed with Oskar 2026-11-07)
- [x] 2.4 B3: causation ID is the triggering message ID
- [x] 2.5 B4 Kafka: retry and DLT (with `MessageFailureStrategy`, agreed with Oskar 2026-11-07)
- [x] 2.6 B4 RabbitMQ: retry and DLQ
- [x] 2.7 B5 Kafka: unreadable messages to the DLT without retry
- [x] 2.8 B5 RabbitMQ: unreadable messages to the DLQ without retry
- [x] 2.5–2.8 follow-ups (agreed with Oskar 2026-11-07): exponential back-off with jitter, the strategy gets the message, a `messaging.dead_lettered` counter and spans in place of log lines, Grafana LGTM behind the `observability` compose profile
- [x] Workshop cleanup (asked by Oskar 2026-11-07):
  - One `docker-compose.yml` at the workshop root, shared by `solved` and `exercises`. It has PostgreSQL, pgAdmin and `otel-lgtm` (`observability` profile). Mongo, EventStoreDB and the `.arm` files are gone. Both CI workflows run `docker compose -f ../docker-compose.yml` and also trigger on changes to it.
  - The main README's title, intro, topics and CI badges describe this workshop. Its exercise list links to the full implementations.
  - The e03 solution README covers failure handling and Grafana.
- [x] Facades traced with `@Observed` (`spring-boot-starter-aspectj`, `management.observations.annotations.enabled=true`). Each saga trace starts at `GroupCheckoutFacade#initiateGroupCheckout`, and facade timers show in Prometheus.
- [x] Todo List solution (asked by Oskar 2026-11-07), following [the To-Do List article](https://event-driven.io/en/to_do_list_and_passage_of_time_patterns_combined/): `GroupCheckoutProjection` is an Emmett-style `Projection` (`canHandle`, `getDocumentId`, `evolve`; null deletes). The projection doesn't know the bus or the to-do list; `GroupCheckoutsConfig` subscribes one handler per event that runs `projection.handle(event)` and passes the stored item to `GroupCheckoutToDoList.handle`, so nothing depends on handler order. `GroupCheckoutToDoList` reads only the item: the next guest's `CheckOutGuest`, or the completion. `GroupCheckoutFacade` only publishes `GroupCheckoutInitiated`. An update for a missing item throws "Entity not found". A repeated step on redelivery is only noted in the README; teach it in later examples.
- [x] Time-based Todo List alternative: `ScheduledGroupCheckoutToDoList` with `@Scheduled`, not plugged in, tested directly in `ScheduledBusinessProcessTests` (agreed with Oskar 2026-11-07). Uses `Database.Collection.findAll()` added to the solved e03 core.
- [x] 2.9 Close step 2: PR, Oskar merges

## Step 3: idiomatic e03 adapters (`refactor/e03-idiomatic-adapters`)

- [-] 3.1–3.4 Dropped by Oskar on 2026-11-07: the broker variants keep their own copies of the domain and scenarios. Instead, the broker `BusinessProcessTests` were aligned with the in-memory exercise, and the waiting moved into the broker `MessageCatcher`
- [x] 3.5 Routing by convention, with optional `MessageRoutes` registrations (agreed with Oskar 2026-11-07). Kafka: configured topic, first record component as key. RabbitMQ: configured exchange, class name as routing key. Registrations by class or `inPackage`; target and key fall back to the convention independently, and `null` means the convention. Each e03 solution declares its routing convention as a `HotelFinancialMessageRoutes` bean (Oskar, 2026-11-09): every message from the module's package goes to the configured topic, keyed by its first field (the entity ID), or to the configured exchange, routed by its class name. Guest stay accounts and group checkouts are one module, so there is one topic and one exchange. Package routing is shown in `KafkaMessageRoutingTest` and `RabbitMQMessageRoutingTest`. Open: the e04/e05 topology in the spec still splits topics and exchanges per module and message kind
- [x] 3.6 Bounded Kafka publish wait
- [x] 3.7 RabbitMQ publisher confirms and routing keys
- [x] 3.8 Drop the manual spans: the propagation tests never asserted on them; the template and listener observations were already on in both configurations
- [x] 3.9 Kafka versioning: done as option 1 (Oskar, 2026-11-07). Reading stays in the listener, not in a `Deserializer`; see the 3.9 note
- [x] 3.10 RabbitMQ versioning: same shape as Kafka, the listener takes the raw `Message` and calls `VersionedMessageConverter.read`
- [x] 3.11 Kafka configuration through Boot: Boot builds the template and the listener factory; see the 3.11 notes for the serializer
- [x] 3.12 RabbitMQ configuration through Boot: Boot builds the template, the listener factory and the retry; see the 3.12 notes
- [x] 3.13 Metadata headers, Kafka: `MessageMetadata.setMetadataHeaders` writes `messageId` and the `correlationId` (generated when the baggage has none); Boot writes `correlationId` and `causationId` from baggage. `KafkaMessageBaggageHandler` only maps the incoming `messageId` to `causationId`. `MessageMetadata` is final, entityId and entityVersion are gone, including from the baggage properties
- [x] 3.14 Metadata headers, RabbitMQ: same shape as Kafka
- [x] 3.15 Test wiring cleanup: `KafkaSagaTestApplication` and `RabbitMQSagaTestApplication`; `messaging.type`, `TestApplicationConfig` and `KafkaPropertiesBootstrapTest` deleted
- [-] 3.16 SagaResult naming: skipped by Oskar on 2026-11-09
- [-] 3.17 Close step 3: skipped by Oskar on 2026-11-09; the CI build is green locally in both projects

### 3e findings (filled by 3.13 and 3.14)

Method: both `MessageMetadataPropagationTest`s, run once with the baggage handlers and once with their `@Component` commented out. Both buses had a temporary probe printing the baggage and headers on each received message. Everything was restored afterwards.

- **Boot already carries baggage across both brokers.** With `remote-fields`, a message published inside a handler carries a W3C `baggage` header (RabbitMQ: `baggage=causationId=…;propagation=unlimited,correlationId=…`), and the consumer gets the values back as baggage.
- **correlationId needs no receive-side code.** Without the handlers, the first hop has no `baggage` header, yet the consumer's `correlationId` baggage equals the `correlationId` header. Boot reads a remote field from a header with the same name. Our publish code still generates the correlationId when there's none.
- **causationId does need our code.** Without the handlers, causationId is null on every hop, because nothing maps the incoming `messageId` to `causationId`. Both tests fail on "causationId should be present".
- **entityId and entityVersion are never set.** They are null on every message, with or without the handlers: nothing puts them in baggage, so the headers are never written. The plumbing exists, but nothing feeds it. Routing (3.5) resolves keys separately and doesn't feed these headers either; 3.13 and 3.14 dropped the entityId and entityVersion baggage path.
- **What's left to keep as custom code:** generating `messageId`, generating `correlationId` at the start of a process, and mapping the incoming `messageId` to `causationId`. Restoring `correlationId` from headers on receive repeats what Boot does.
- **Boot writes the remote fields as plain headers too.** A record published with `correlationId` and `causationId` in baggage carries one `correlationId` and one `causationId` header, besides `baggage` and `traceparent`. On Kafka, Boot's header replaces the one our code added, so there is no duplicate (checked 2026-11-09 by printing the raw record headers in `KafkaMessageMetadataTest`; the same on RabbitMQ). That's why our code no longer writes `causationId`.

## Step 4: e04 Spring Kafka (`feat/e04-spring-kafka`)

- [ ] 4.1 Test infrastructure and topology
- [ ] 4.2 Facades send with KafkaTemplate (plus the dual-write comment)
- [ ] 4.3 Command listeners
- [ ] 4.4 Saga listener and scenarios
- [ ] 4.5 Dead-letter topics
- [ ] 4.6 Deciders: facades and command listeners
- [ ] 4.7 Deciders: saga, scenarios, DLT
- [ ] 4.8 Derive the exercise (aggregates)
- [ ] 4.9 Derive the exercise (deciders), README, PR, Oskar merges

## Step 5: e04 Spring AMQP (`feat/e04-spring-rabbitmq`)

- [ ] 5.1 Test infrastructure and topology
- [ ] 5.2 Facades send with RabbitTemplate (plus the dual-write comment)
- [ ] 5.3 Command listeners
- [ ] 5.4 Saga listener and scenarios
- [ ] 5.5 Retry and dead-lettering
- [ ] 5.6 Deciders: facades and command listeners
- [ ] 5.7 Deciders: saga, scenarios, DLQ
- [ ] 5.8 Derive the exercises, README, PR, Oskar merges

## Step 7: e06 Spring Modulith (`feat/e06-spring-modulith`)

- [ ] 7.1 Dependencies, containers, module boundaries (needs D-e06)
- [ ] 7.2 Guest stay accounts through JPA
- [ ] 7.3 Externalized guest stay account events (registry mode)
- [ ] 7.4 Group checkouts, saga, end-to-end scenarios
- [ ] 7.5 Broker down at commit (registry mode)
- [ ] 7.6 Real outbox mode
- [ ] 7.7 Deciders flavour with JSON state
- [ ] 7.8 Derive the exercise, README, PR, Oskar merges

### 7.5 notes and 7.6 decision

_Not recorded yet._

## Step 8: docs and CI (`docs/messaging-exercises`)

- [ ] 8.1 READMEs
- [ ] 8.2 CI timings, PR, Oskar merges

## Step 6: e05 Spring Cloud Stream (`feat/e05-spring-cloud-stream`, runs last)

- [ ] 6.0 GA gate: the Spring Cloud train for Boot 4.1 is GA
- [ ] 6.1 Dependencies, two-binder bases, binder behaviour verified
- [ ] 6.2 Polymorphic events and StreamBridge
- [ ] 6.3 Command consumers
- [ ] 6.4 Saga functions and scenarios on both binders
- [ ] 6.5 Error handling on both binders
- [ ] 6.6 Deciders flavour
- [ ] 6.7 Derive the exercise, docs, PR, Oskar merges

### 6.0 GA check

_Not recorded yet._

## Noticed, not done

_Things found along the way that are out of scope for the current prompt._

- (2.3) In `sagas_kafka/solution1_aggregates/BusinessProcessTests` and `solution2_immutableentities/BusinessProcessTests`, three of the four scenarios were commented out entirely, and only the first ran against Kafka. Fixed at the start of step 3 (asked by Oskar 2026-11-07): the commented code didn't compile (unmatched brackets, the old `GuestCheckoutCompleted`/`GuestCheckoutFailed` event names). With those fixed, all 4 scenarios pass in both flavours. Their Given steps were then aligned with the in-memory ones (the third guest had a payment without a charge instead of a charge with half a payment). The broker `MessageCatcher.shouldReceiveMessages` now waits up to 10 s itself, so the broker `BusinessProcessTests` have no `await()` and their bodies match the in-memory exercise.
- (2.3) Spring Boot already carries `correlationId` between messages, because `management.tracing.baggage.remote-fields` lists it. So no test can show that the RabbitMQ handler failed to read the header. With `remote-fields` cleared, the propagation test fails on "correlationId should not be empty". The handler now maps the header, as Kafka's does. Step 3e decides whether metadata travels in our headers, in Boot's baggage or in both.
- (2.5) `KafkaMessageBus.handleMessage` ended its `messagebus.handle` span only on RabbitMQ's side. Fixed in the follow-ups, because the unended span broke the Kafka trace in Grafana. 3.8 still drops the manual spans.
- (2.5) The Kafka dead-letter recoverer publishes through a separate `KafkaTemplate<String, String>`. The shared template's JSON serializer would re-encode the raw value as a JSON string and add a `java.lang.String` type header. 3.9 and 3.11 change serialization and may make the separate template unnecessary.
- (2.5) Spring Kafka's `DefaultErrorHandler` has its own list of fatal exceptions (`MessageConversionException`, `ClassCastException` and others) that skip retries before the back-off function runs. An unknown message type has `MessageConversionException` in its cause chain, so the strategy was never asked. `KafkaConfiguration` clears that list with `setClassifications(Map.of(), true)`, so `MessageFailureStrategy` is the only place that decides.
- (follow-ups) `ExponentialBackOffWithMaxRetries` stops on a precomputed elapsed time, not on a retry count. With jitter, delays come out shorter and an extra retry fits in, so a poison message was attempted 4 times. Kafka now uses Framework 7's `ExponentialBackOff` with `setMaxAttempts(2)`.
- (follow-ups) Both brokers shared one `BackOff` from `core/RetryBackOff` until 3.12; now only Kafka uses it. `RetryBackOffTest` checks the delays without timing anything. With jitter, Framework 7 adds 0–50% to the first delay, then ±jitter scaled by the multiplier, so the second wait can be shorter than the first.
- (follow-ups) RabbitMQ's stateless retry runs inside one delivery, so a trace shows one failed `receive` span. Since 3.8 dropped the manual spans, the three attempts no longer show as child spans. Kafka shows three failed `process` spans, one per redelivery. The error tests assert both.
- (follow-ups) The Kafka and RabbitMQ `BusinessProcessTests`, propagation tests and error-handling tests send traces with `-Pobservability`. The error-handling tests also push `messaging.dead_lettered` (`@AutoConfigureMetrics` with `micrometer-registry-otlp`), which shows in Prometheus as `messaging_dead_lettered_total`.
- (cleanup) `observability/tempo-config.yaml` is the config from `grafana/otel-lgtm:0.39.0` plus three overrides. Search was ~30 s behind because the query frontend leaves out the last 30 s by default (`query_end_cutoff`). With `max_trace_idle` and `flush_check_period` at 1 s as well, traces are searchable 1.5–2 s after they end. When bumping the image, start from its new config and reapply the overrides.
- (cleanup) Measuring with Gradle: rerunning the same test filter with no changes leaves `test` UP-TO-DATE and nothing runs. Use `--rerun`.
- (follow-ups) A nested `@TestConfiguration` is picked up by the tests' explicit `@ComponentScan` of its package, so it leaks into every test there. The propagation tests' `@Primary` tracer did this. Test-only beans are now plain classes added with `@Import` (`InMemorySpansConfiguration`, `RecordingFailureStrategy.Config`).
- (2.6) The RabbitMQ variant builds its own `SimpleRabbitListenerContainerFactory`, so the retry interceptor is set with `setAdviceChain` and not through `spring.rabbitmq.listener.simple.retry.*`. 3.12 moves to Boot's factory and plugs the strategy into a `RabbitListenerRetrySettingsCustomizer`.
- (3.6) The test `log4j2.xml` console appender now has `follow="true"`. Without it, Log4j2 keeps the `System.out` it saw at startup, and `OutputCaptureExtension` never sees the log lines the tests must assert on.
- (3.7) `RabbitConfig` still builds its own `RabbitTemplate`, so `setMandatory(true)` is set there and not through `spring.rabbitmq.template.mandatory`. 3.12 moves it to the property.
- (3.7) Routing key = the class name, the same string as the `__TypeId__` header and the `message.type` metric tag. The saga queue is still bound with `#`.
- (3.x) The test `log4j2.xml` has `status="DEBUG"`, so every test JVM prints Log4j2's own start-up configuration to stderr. Mockito also warns about self-attaching. Both are noise in every run; not touched.
- (3.x) In `sagas/solution2_immutableentities/BusinessProcessTests` (in-memory), the "all stays settled" scenario records the third guest's charge and two half payments on `guestStays[0]` instead of `guestStays[2]`. The broker copies now match it. The test passes, but guest 3 has no activity. Not touched, as asked: tests stay as they are.
- (3.9) Reading in Spring's layer loses traces. With `ErrorHandlingDeserializer` or a `RecordMessageConverter`, an unreadable record fails before the listener adapter's `invoke()`. In Spring Kafka 4.1.2 the adapter owns the listener observation (`isListenerAdapterObservationAware`), so the container neither records that error nor stops the observation (`KafkaMessageListenerContainer.doInvokeRecordListener`, `MessagingMessageListenerAdapter.invoke`). The unreadable message then leaves no span. Worth reporting upstream. Oskar chose to keep reading in the listener: the consumer reads `byte[]`, and `VersionedMessageConverter.read` turns it into the message or throws `UnreadableMessageException`.
- (3.9/3.10) Two reading bugs fixed test-first in both brokers (`VersionedMessageConverterTest`):
  - a registered transformation handed subscribers an `Optional` instead of the message, and a failing transformation gave `Optional.empty()` instead of "unreadable";
  - registering one type name narrowed the trusted packages to that class's package, so unregistered classes from other packages became unreadable. Trusted packages are now `app.messaging.trusted-packages` (default `*`, as before when nothing is registered); registered names map to their class without a trust check.
- (3.9) The Kafka dead-letter template now sends `byte[]`, the raw value the listener received, instead of `String`.
- (3.9) A listener parameter of type `Object` gets the `ConsumerRecord`, because Spring resolves provided arguments by type before converting. Use `ConsumerRecord` or `Message<?>`.
- (2.8) The RabbitMQ listener factory no longer sets `JacksonJsonMessageConverter`. The container ran it on every message before the listener, even though the listener takes the raw `Message`, so bad payloads failed inside Spring and never reached `TransformingMessageDeserializer`. `RabbitTemplate` still uses the converter for publishing.
- (3.8) `RabbitTemplate`'s observation ends when the message is written to the channel, before the publisher confirm. A nack, a return or a confirm timeout fails `publish`, but the send span stays green. Kafka's send span records the broker's failure.
- (3.8) Spring AMQP 4.1 deprecates `Confirm.isAck()`/`getReason()` for removal; the bus uses the record accessors `ack()`/`reason()`.
- (3.11) The value serializer is set in one place: the `DefaultKafkaProducerFactoryCustomizer` in `MessageSerializationConfiguration`. The plan said to delete it, but Boot has no property that gives a serializer our `JsonMapper` and the registry's type mapper. Without them, registered type names stop being written. The misleading `spring.kafka.producer.value-serializer=StringSerializer` property is gone instead.
- (3.11) The dead-letter `KafkaTemplate` is built inside the error handler bean, not declared as a bean. Boot's `kafkaTemplate` is `@ConditionalOnMissingBean(KafkaTemplate.class)`, so a second template bean would switch it off.
- (3.11) Boot has no property for the delivery-attempt header, so a `ContainerCustomizer` bean sets it. Boot's factory picks up the `DefaultErrorHandler` as the single `CommonErrorHandler`.
- (3.11) `app.kafka.*` binds to `KafkaMessageBusProperties`; the defaults live in the test `application.properties`. The `@KafkaListener` uses `${app.kafka.group-id}`, while tests set `app.kafka.groupId`. Boot documents that a kebab-case placeholder matches camelCase keys; not checked against the broker's consumer groups.
- (3.11) `app.messaging.retry.initial-interval` and `app.messaging.trusted-packages` stay as `@Value`: they are shared by both brokers, not `app.kafka`.
- (3.11) Not touched: `src/test/java/resources/log4j2.xml` is a stray copy from the template commit and is not on the classpath. The test `MessageCatcher` and `EntityDefinitionTests` still print with `System.out`, as the in-memory exercise does.
- (3.12) The JSON converter is not a `MessageConverter` bean. Boot sets a single converter bean on both the template and the listener factory, and the factory then converts every message before the listener. The unreadable-message tests caught it: the strategy was asked three times, because it saw a conversion error instead of `UnreadableMessageException`. A `RabbitTemplateCustomizer` sets the converter on the template, and `VersionedMessageConverter` reads with `rabbitTemplate.getMessageConverter()`, the same converter publishing writes with. There's a comment on the customizer explaining this.
- (3.12) Retry comes from `spring.rabbitmq.listener.simple.retry.*` (enabled, max-retries=2, multiplier=2, initial-interval=`${app.messaging.retry.initial-interval}`). Framework 7's `RetryPolicy.Builder` refuses a custom `BackOff` combined with delay or max-retries, which Boot always sets, so RabbitMQ can't reuse `RetryBackOff`. A `RabbitListenerRetrySettingsCustomizer` adds the jitter (half the delay, as `RetryBackOff` does) and asks `MessageFailureStrategy` through the exception predicate. The dead-letter counter and `RejectAndDontRequeueRecoverer` are the `MessageRecoverer` bean that Boot uses.
- (3.12) Mandatory publishing and both observations are properties now (`spring.rabbitmq.template.mandatory`, `spring.rabbitmq.template.observation-enabled`, `spring.rabbitmq.listener.simple.observation-enabled`). The topology beans take `RabbitMQMessageBusProperties` (`app.rabbitmq.exchange`, `app.rabbitmq.publish-timeout`), and the listener no longer names a container factory.
- (3.12) Full `solved` run after 3.8-3.12: 161 tests, 0 failed.
- (3.15) Each broker has one test application in its `core` package. It scans `e03_businessprocesses.core` and the broker's `core`, and imports the broker's container. The solution packages hold facades, type mappings and a configuration class, so each solution configuration (`KafkaSolution1Configuration`, `RabbitMQSolution1Config` and the solution2 ones) now has `@ComponentScan` of its own package. Tests name both: `@SpringBootTest(classes = {KafkaSagaTestApplication.class, KafkaSolution1Configuration.class})`. A plain `@ComponentScan` doesn't apply Boot's test-type filter, so the scan also finds `KafkaTestConfiguration`; the `@Import` keeps it explicit.
- (3.15) Full `solved` run: 158 tests, 0 failed (161 before, minus the 3 in `KafkaPropertiesBootstrapTest`).
