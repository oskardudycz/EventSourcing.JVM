# Plan: Spring Boot 4, messaging bug fixes, idiomatic Spring messaging exercises

Source spec: [spec-spring-messaging.md](spec-spring-messaging.md). State tracking: [todo.md](todo.md).

Path shorthand (same as the spec):
- `S/` = `solved/src/test/java/io/eventdriven/eventdrivenarchitecture/`
- `X/` = `exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/`

## 1. Blueprint

The work runs in the order the spec decided: bump, bugs, idiomatic e03 adapters, new exercises, docs. One branch per step, off `main`, merged before the next step starts.

| Step | Branch | Outcome |
|---|---|---|
| 0 | none | Baseline test numbers recorded in `todo.md` |
| 1 | `chore/spring-boot-4` | Both projects on Boot 4.1.2 and Jackson 3, unused dependencies gone, same test results |
| 2 | `fix/e03-broker-bugs` | B1–B5 fixed, each proven by a test that failed first |
| 3 | `refactor/e03-idiomatic-adapters` | One scenario suite runs on in-memory, Kafka and RabbitMQ; adapters read like Spring code |
| 4 | `feat/e04-spring-kafka` | Native Spring Kafka exercise, both domain flavours |
| 5 | `feat/e04-spring-rabbitmq` | Native Spring AMQP exercise, both domain flavours |
| 7 | `feat/e06-spring-modulith` | Dual write and outbox with Modulith, JPA and Kafka |
| 8 | `docs/messaging-exercises` | READMEs and CI cover e04 and e06 |
| 6 | `feat/e05-spring-cloud-stream` | Spring Cloud Stream on both binders. **Runs last** (Q1): it waits for the Spring Cloud train for Boot 4.1 to go GA, and it adds its own README line and CI check |

### What the code looks like today (facts the prompts rely on)

- The e03 saga variants live in `S/e03_businessprocesses/sagas/` (in-memory), `sagas_kafka/` and `sagas_rabbitmq/`. The broker variants carry full copies of the domain (`solution1_aggregates`, `solution2_immutableentities`), their own `SagaResult`, `MessageCatcher` and `versioning/` package.
- The in-memory `sagas/solution*/BusinessProcessTests` build everything by hand in `@BeforeEach` (`new InMemoryMessageBus()`, `configureGroupCheckouts(messageBus, facade)`). The Kafka and RabbitMQ copies are `@SpringBootTest(classes = TestApplicationConfig.class)` plus `@ComponentScan` plus `@Import(...TestConfiguration.class)`, and already use Awaitility.
- Both `MessageMetadataPropagationTest`s import facades and events from their own broker's `solution1_aggregates` copy.
- `exercises` has e03 in a flatter layout (`X/e03_businessprocesses/solution1_aggregates/`, no `sagas/` level), has no saga, no `SagaResult`, and no Kafka, AMQP, Testcontainers or Awaitility dependencies.
- `solved/build.gradle`: Boot 3.4.2, Java 22, explicit versions everywhere, `spring-kafka` without a starter, Testcontainers 1.20. `exercises/build.gradle`: Java 21, the same unused dependencies.
- CI (`.github/workflows/workshops_event-driven-architecture-*.yml`) runs `docker compose up -d` and `./gradlew build -Pci=true`. `-Pci=true` excludes `@Tag("Exercise")`.

### Decision the spec doesn't settle: confirm before step 7

**D-e06: how `CheckOutGuest` commands reach guest stay accounts in e06.** The spec says group checkouts consume `guest-stay-account-events` with `@KafkaListener`, but not how the saga's fan-out of `CheckOutGuest` travels. Sending it with `KafkaTemplate` from a listener would reintroduce the dual write the exercise is about.

Confirmed by Oskar on 2026-11-06: `CheckOutGuest` stays a command. Group checkouts send it as a message on `guest-stay-account-commands`, the receiver's topic, as in e04. The transport is Modulith's externalization, which only accepts messages through `ApplicationEventPublisher`: the command record is passed to `publishEvent(...)` inside the transaction and annotated `@Externalized("guest-stay-account-commands::#{guestStayAccountId()}")`. Guest stay accounts consume that topic with `@KafkaListener`. Every message between modules, events and commands alike, then goes through the outbox.

Modulith's API names every message an "event" (`ApplicationEventPublisher`, event publication registry). The e06 README says so explicitly: the API name describes the mechanism, not what the message means.

Prompt 7.1 stops and asks Oskar if this hasn't been confirmed in `todo.md`.

## 2. How the steps were sized

**Round 1** gave the nine spec steps. Most were too large for one safe change: step 1 bumps Boot, Jackson, Testcontainers and the Kafka module at once, and step 3 has seven sub-sections.

**Round 2** split each step into chunks that each end green:
- Step 1: remove unused dependencies and drop BOM-managed versions on Boot 3.4 first. Then bump `solved` with Boot 4's Jackson 2 compatibility module, and only then move `solved` to Jackson 3. This avoids one big red bang. `exercises` comes last: its build file is copied from `solved` and trimmed, and its code gets the same import moves.
- Every step works in `solved` first. `exercises` is derived from it by deleting what attendees build, never written on its own.
- Step 2: one chunk per bug per broker.
- Step 3: unify the scenario suite first, because it removes the duplicate domain that every later sub-step would otherwise touch twice. Then publishing, receiving, configuration, metadata, cleanup and naming.
- Steps 4–7: shared test infrastructure, then send, then command listeners, then saga listener and scenarios, then error handling. Then the second domain flavour, then the exercise derivation.

**Round 3** checked each chunk against two questions. Can it fail in one obvious way? Does it leave nothing orphaned? This split three more chunks:
- the ArchUnit setup from the B1 fix;
- the Kafka key from the bounded wait;
- the e06 registry mode from the outbox mode.

It also merged two pairs that were too thin to stand alone:
- the dual-write comment goes into the facade prompt;
- `ConcurrentHashMap` goes into the receiving prompts.

The result is 68 prompts. Each changes one concern and ends with all tests green and the new code reachable from a test.

## 3. Conventions every prompt follows

Each prompt below says "Follow the plan conventions". That means:

1. Read `spec-spring-messaging.md` (the section named in the prompt) and this file's section 1 before touching code.
2. TDD: write the failing test, run it, and confirm it fails for the expected reason. Then write the minimal code, see it pass, and refactor.
3. Run tests from the project folder after `docker compose up -d`, with `./gradlew test --tests '<pattern>'` while working and the full `./gradlew build -Pci=true` before finishing. Test output must be clean. If a test deliberately causes errors, capture the log (Spring Boot's `OutputCaptureExtension` / `CapturedOutput`) and assert on it, so the noise is expected rather than ignored.
4. Library APIs: check the current Spring Boot 4.1, Spring Kafka 4.1, Spring AMQP 4.1, Spring Modulith 2.1, Spring Cloud Stream and Testcontainers 2 docs (Context7 or the reference docs). Don't guess class or property names.
5. Smallest change that meets the prompt. No unrelated fixes; note them in `todo.md` under "Noticed, not done". No comments unless the code is genuinely tricky. Never name anything "new", "improved" or similar.
6. Constructor injection, SLF4J loggers, records where they fit, and the formatting of the surrounding file.
7. Finish by ticking the prompt in `todo.md` and committing with a conventional, imperative message that says why. No co-author trailer. Never `--no-verify`. If a hook fails, read its output, fix the cause and rerun.
8. If something in the prompt turns out to be wrong about the code, stop and report it instead of improvising.
9. `solved` first, always. Build and test the change there. `exercises` is then derived from `solved` by copying and deleting what attendees build, leaving TODOs. Never write exercise code that has no solved counterpart.

---

## 4. Prompts

### Step 0: Baseline

#### Prompt 0.1: Record the baseline

```text
Follow the plan conventions in workshops/event-driven-architecture/plan.md. Spec section: "Step 0: Baseline".

Don't change any code. In workshops/event-driven-architecture/solved and then in workshops/event-driven-architecture/exercises:
1. Run `docker compose up -d`.
2. Run `./gradlew clean test` and then `./gradlew build -Pci=true`.
3. From build/test-results (the XML reports), count the tests run, passed, failed and skipped, per project and per task.
4. Note the noise in the output: warnings, deprecation notices, stack traces printed by passing tests, and anything printed with System.out/System.err.

Write the results into the "Baseline" section of workshops/event-driven-architecture/todo.md: one table per project, then a bullet list of the noise, each item with the class or test that produced it. Confirm the two known facts from the spec: the RabbitMQ MessageMetadataPropagationTest is @Disabled, and the exercise tests fail locally by design. If anything else fails, list it as a pre-existing failure. Don't fix it.

Tick 0.1 in todo.md. Don't commit; todo.md is working state.
```

### Step 1: Bump to Spring Boot 4 (branch `chore/spring-boot-4`)

#### Prompt 1.1: Remove unused dependencies

```text
Follow the plan conventions. Spec section: "Step 1", task 3. Create branch chore/spring-boot-4 off main.

Change solved/build.gradle first, then make the same removals in exercises/build.gradle. Remove these dependencies:
- springdoc-openapi-starter-webmvc-ui
- spring-retry
- com.eventstore:db-client-java
- org.mongodb:mongodb-driver-sync
- org.testcontainers:mongodb

Before removing each one, grep both src trees for its packages (org.springdoc, org.springframework.retry, com.eventstore, com.mongodb, org.testcontainers.containers.MongoDBContainer) and confirm zero usages. If any usage exists, stop and report it.

Then grep src/test/resources/application.properties in both projects for properties only these libraries read (esdb.connectionstring, mongodb.connectionstring). Remove them only if no code reads them.

Leave docker-compose files alone.

Verify: `./gradlew build -Pci=true` in both projects gives the same counts as the baseline in todo.md.

Commit: "chore(eda-workshop): remove unused dependencies" with a body saying they were never used and only slowed builds and the upcoming Boot 4 bump.
```

#### Prompt 1.2: Let the Boot BOM manage versions (still Boot 3.4)

```text
Follow the plan conventions. Spec section: "Step 1", task 2. Stay on branch chore/spring-boot-4.

In solved/build.gradle first, then the same in exercises/build.gradle, drop the explicit version from every dependency that the Spring Boot 3.4.2 BOM manages. That covers:
- the spring-boot-starter-* artifacts
- jackson-*
- log4j-api and log4j-core
- micrometer-tracing-*
- opentelemetry-*
- postgresql
- assertj-core
- spring-kafka
- the Testcontainers artifacts

To check that the BOM manages something, run `./gradlew dependencies --configuration testRuntimeClasspath` before and after the change. Keep the version on net.datafaker:datafaker and on anything the BOM doesn't manage. Also remove testImplementation lines that only repeat an implementation dependency (jackson-databind, jackson-core).

Expect small version moves (for example Jackson 2.17 to the BOM's 2.18). Note them in the commit body.

Verify: both projects give the same test counts as the baseline.

Commit: "chore(eda-workshop): let the Spring Boot BOM manage dependency versions".
```

#### Prompt 1.3: Bump the solved project to Boot 4.1.2 (Jackson 2 compatibility for now)

```text
Follow the plan conventions. Spec section: "Step 1", tasks 1, 4, 5, 7, 9. Stay on branch chore/spring-boot-4.

Work only in solved/. exercises follows in 1.5, derived from the result. This prompt changes Boot, Kafka and Testcontainers. Jackson stays on 2 through Boot 4's Jackson 2 compatibility support, so the test suite can tell us whether the platform bump alone broke anything. Prompt 1.4 moves to Jackson 3.

1. Set the plugin to 4.1.2. Keep io.spring.dependency-management at 1.1.7.
2. Replace org.springframework.kafka:spring-kafka with org.springframework.boot:spring-boot-starter-kafka. Without the starter, Boot 4 doesn't load the Kafka auto-configuration.
3. Add Boot 4's Jackson 2 compatibility module. Find its exact name in the Boot 4 migration guide.
4. Testcontainers 2:
   - Switch to the renamed artifacts (testcontainers-kafka, testcontainers-rabbitmq, testcontainers-junit-jupiter).
   - In S/e03_businessprocesses/sagas_kafka/core/KafkaTestConfiguration.java, replace org.testcontainers.containers.KafkaContainer with org.testcontainers.kafka.ConfluentKafkaContainer.
   - Move RabbitMQContainer to its Testcontainers 2 package.
   - Keep @ServiceConnection working.
5. Fix moved imports. Expect at least KafkaProperties, KafkaConnectionDetails, DefaultKafkaProducerFactoryCustomizer, @ServiceConnection and @AutoConfigureObservability. Take each new package from the migration guide.
6. Keep Java 22.
7. Read the test startup log and rename any properties Boot 4 changed. In particular, check the management.tracing.* and management.otlp.* keys in src/test/resources/application.properties.

Verify: `./gradlew build -Pci=true` gives the baseline counts. The RabbitMQ propagation test is still @Disabled, which is expected.

If the Kafka or RabbitMQ tests break in ways that aren't import or rename issues, stop and report the failure with its output.

Commit: "chore(eda-workshop): move solved to Spring Boot 4.1".
```

#### Prompt 1.4: Move solved to Jackson 3

```text
Follow the plan conventions. Spec section: "Step 1", task 6. Stay on branch chore/spring-boot-4.

In solved/:
1. Remove the Jackson 2 compatibility module added in 1.3, and drop jackson-datatype-jsr310.
2. Move databind and core imports to tools.jackson.*. Annotations stay in com.fasterxml.jackson.annotation. Replace dependency coordinates with the Jackson 3 ones the Boot 4 BOM manages.
3. Switch Spring Kafka and Spring AMQP to their Jackson 3 classes: the JSON serializer, deserializer, type mapper and AMQP message converter. Take the exact class names from the Spring Kafka 4.1 and Spring AMQP 4.1 reference docs. Keep the trusted packages and type mappings exactly as they are now.
4. Replace hand-built ObjectMapper instances with JsonMapper. Where Spring already provides a JsonMapper bean, inject it instead.

Verify:
- Baseline counts hold.
- grep finds no com.fasterxml.jackson.databind or com.fasterxml.jackson.core imports in solved/src.
- The compile output has no deprecation warnings from our code.

Commit: "chore(eda-workshop): move solved to Jackson 3". Body: Boot 4's Jackson 2 support is deprecated, and Spring Kafka and AMQP 4 default to Jackson 3.
```

#### Prompt 1.5: Derive the exercises build from solved

```text
Follow the plan conventions. Spec section: "Step 1", tasks 1, 6, 7, 9. Stay on branch chore/spring-boot-4.

exercises follows solved; it is never migrated on its own.

1. Replace exercises/build.gradle with a copy of solved/build.gradle, then delete what exercises doesn't use yet:
   - the Kafka, AMQP, Testcontainers and tracing dependencies;
   - ArchUnit, if it is already there.
   Before deleting each one, grep X/ to confirm it isn't used. Keep the Java toolchain at 21 and keep exercises' own group/version lines. Later steps add the messaging dependencies back when they derive e04–e06.
2. Apply to X/ the same code moves solved got in 1.3 and 1.4: the Jackson 3 imports and JsonMapper, and the moved Boot 4 imports. Where a file in X/ has a counterpart in S/ that already differs only by the TODOs, copy the migrated imports from S/.
3. Make exercises/src/test/resources/application.properties match the property renames done in solved, for the keys exercises has.

Verify:
- `./gradlew build -Pci=true` passes with the baseline counts.
- `./gradlew test` fails only in the Exercise-tagged tests, the same set as the baseline.
- There are no deprecation warnings from our own code in the compile output.

Commit: "chore(eda-workshop): move exercises to Spring Boot 4.1 and Jackson 3". Body: exercises mirror solved; Boot 3.4 support is ending, and the planned exercises need Spring Modulith 2.1, which requires Boot 4.
```

#### Prompt 1.6: Close step 1

```text
Follow the plan conventions. Spec section: "Step 1", "Done when". Stay on branch chore/spring-boot-4.

1. Run `./gradlew clean build -Pci=true` in both projects. Compare the counts with the baseline in todo.md.
2. Compare the output noise with the baseline noise list. Anything new that comes from our own code (deprecations, warnings, property-rename notices) gets fixed here. Anything new from libraries gets listed in todo.md under "Noticed, not done".
3. Push the branch and open a PR to main titled "Move EDA workshop to Spring Boot 4.1". The description holds the baseline table from todo.md, the after numbers, and the reason for the change: Boot 3.4 support is ending, and Modulith 2.1 needs Boot 4.
4. Tick 1.6 and stop. Oskar merges before step 2 starts.
```

### Step 2: Fix bugs in the e03 broker variants (branch `fix/e03-broker-bugs`)

#### Prompt 2.1: ArchUnit guard for the Kafka variant

```text
Follow the plan conventions. Spec section: "Step 2", B1. Create branch fix/e03-broker-bugs off the updated main.

1. Add com.tngtech.archunit:archunit-junit5 as testImplementation in solved/build.gradle. Use the latest release that supports JUnit 5 on Java 22, with an explicit version, since the Boot BOM doesn't manage it.
2. Create S/e03_businessprocesses/BrokerIsolationTests.java with @AnalyzeClasses on the e03_businessprocesses package, importing tests too (ImportOption.OnlyIncludeTests or no exclusion, because these sources live in src/test).
3. Add one rule: no class in ..sagas_kafka.. depends on org.springframework.amqp.., com.rabbitmq.. or ..sagas_rabbitmq...

This rule should already pass. Prove it can fail: temporarily add a RabbitMQ import to a sagas_kafka class, watch the rule fail, then revert.

Commit: "test(eda-workshop): guard the Kafka saga variant against RabbitMQ dependencies".
```

#### Prompt 2.2: B1, the RabbitMQ variant must not depend on Kafka

```text
Follow the plan conventions. Spec section: "Step 2", B1. Branch fix/e03-broker-bugs.

1. Add a second rule to BrokerIsolationTests: no class in ..sagas_rabbitmq.. depends on org.apache.kafka.., org.springframework.kafka.. or ..sagas_kafka... Run it and confirm it fails. Record the violations it lists.
2. Fix them with the smallest changes:
   - sagas_rabbitmq/core/RabbitMessageBaggageHandler: use sagas_rabbitmq's own MessageMetadata, and drop ProducerRecord.
   - sagas_rabbitmq/core/messages/MessageMetadata: replace the Kafka Headers and ConsumerRecord helpers with AMQP MessageProperties-based ones. Keep the public method names where callers use them.
   - sagas_rabbitmq/core/versioning/MessageSerializationConfiguration: delete the Kafka producer customizer. Keep only the RabbitMQ beans the variant actually uses. If none remain, delete the class.
3. Run the RabbitMQ BusinessProcessTests and both broker test sets. All must stay green.

Commit: "fix(eda-workshop): remove Kafka dependencies from the RabbitMQ saga variant". Body: the RabbitMQ variant only worked because Kafka happened to be on the classpath, so attendees reading it learned Kafka types.
```

#### Prompt 2.3: B2, RabbitMQ correlation ID propagation

```text
Follow the plan conventions. Spec section: "Step 2", B2. Branch fix/e03-broker-bugs.

1. Remove @Disabled("TODO: Check propagation") from S/e03_businessprocesses/sagas_rabbitmq/core/MessageMetadataPropagationTest. Run it and confirm it fails on the correlationId assertion.
   - If it fails for another reason first (setup or timing), stop and report the output. Don't paper over it.
2. In RabbitMessageBaggageHandler.onScopeOpened, map CORRELATION_ID into baggage the same way KafkaMessageBaggageHandler does. Today it maps CAUSATION_ID onto itself.
3. Run the test until it passes. If the causation assertions now fail, leave them for 2.4: mark only those assertions with a clear failing message and report back. Don't weaken them.

Commit: "fix(eda-workshop): propagate correlation ID through RabbitMQ".
```

#### Prompt 2.4: B3, causation ID must be the triggering message ID

```text
Follow the plan conventions. Spec section: "Step 2", B3. Branch fix/e03-broker-bugs.

1. In both MessageMetadataPropagationTest classes (Kafka and RabbitMQ), strengthen the causation check. Capture the messageId of each CheckOutGuest command as it's handled, and assert that the causationId of the GuestCheckedOut it produced equals that messageId.
   - Today the Kafka test only checks that the causationId isn't the initiating message's ID. Replace that weak assertion.
2. Run both and confirm they fail for the reason in the spec: setHeadersFromBaggage fills CAUSATION_ID from entityId baggage.
3. Fix setHeadersFromBaggage in both handlers so CAUSATION_ID comes from the causation baggage, which holds the incoming message's messageId. If the incoming messageId isn't in baggage yet, put it there in onScopeOpened.
4. Both propagation tests are green, and so is everything else.

Commit: "fix(eda-workshop): set causation ID to the ID of the message being handled".
```

#### Prompt 2.5: B4 Kafka, failed handlers retry and dead-letter

```text
Follow the plan conventions. Spec section: "Step 2", B4. Branch fix/e03-broker-bugs.

1. Write S/e03_businessprocesses/sagas_kafka/core/KafkaErrorHandlingTest:
   - a Spring Boot test using the existing Kafka test configuration;
   - subscribe a handler through KafkaMessageBus that throws for one specific message and records every attempt;
   - publish the poison message, then a healthy one;
   - assert: the poison message was attempted more than once and a bounded number of times; it arrives on <topic>.DLT (read it with a plain consumer); the healthy message is handled;
   - capture the log with OutputCaptureExtension and assert the expected error line appears.
   Run it and confirm it fails, because the listener catches and prints the exception.
2. Fix:
   - remove the catch-all in the Kafka listener;
   - add a DefaultErrorHandler bean with a DeadLetterPublishingRecoverer and a FixedBackOff (for example 3 attempts, 100 ms). Wire it into the container factory the variant uses today (step 3 removes that factory);
   - create <topic>.DLT as a NewTopic bean.
3. All Kafka tests green, and the output carries no unasserted stack traces.

Commit: "fix(eda-workshop): retry and dead-letter failed Kafka messages instead of losing them".
```

#### Prompt 2.6: B4 RabbitMQ, failed handlers retry and dead-letter

```text
Follow the plan conventions. Spec section: "Step 2", B4. Branch fix/e03-broker-bugs.

1. Write S/e03_businessprocesses/sagas_rabbitmq/core/RabbitMQErrorHandlingTest, mirroring 2.5:
   - a poison message is retried a bounded number of times, then lands in a DLQ bound to a dead-letter exchange;
   - the next message is handled;
   - the error log is captured and asserted.
   Confirm it fails.
2. Fix:
   - remove the catch-all in the RabbitMQ listener;
   - set spring.rabbitmq.listener.simple.retry.enabled=true, with max-attempts and initial-interval, in the variant's test properties;
   - add a RejectAndDontRequeueRecoverer bean (check in the Boot 4.1 docs how to plug it into the auto-configured retry);
   - declare the saga queue with x-dead-letter-exchange, plus the dead-letter exchange, DLQ and binding as beans.
   If the variant builds its own SimpleRabbitListenerContainerFactory, which ignores those properties, configure the retry interceptor on that factory for now and leave a todo.md note for 3.12.
3. All RabbitMQ tests green, with clean output.

Commit: "fix(eda-workshop): retry and dead-letter failed RabbitMQ messages instead of losing them".
```

#### Prompt 2.7: B5 Kafka, unreadable messages go straight to the DLT

```text
Follow the plan conventions. Spec section: "Step 2", B5. Branch fix/e03-broker-bugs.

1. Extend KafkaErrorHandlingTest with two cases, sent with a raw KafkaTemplate<String, String>:
   - a record with an unknown message type header;
   - a record with broken JSON.
   For each, assert that it lands on the DLT after exactly one attempt and that the next healthy message is handled. Capture and assert the logs. Confirm both fail.
2. Fix: make TransformingMessageDeserializer (or the listener's deserialization path) throw instead of printing and returning null. Register the thrown exception type with errorHandler.addNotRetryableExceptions(...).
3. All green.

Commit: "fix(eda-workshop): dead-letter unreadable Kafka messages without retrying".
```

#### Prompt 2.8: B5 RabbitMQ, unreadable messages go straight to the DLQ

```text
Follow the plan conventions. Spec section: "Step 2", B5. Branch fix/e03-broker-bugs.

1. Extend RabbitMQErrorHandlingTest with the same two raw messages (unknown type, broken JSON), sent with RabbitTemplate.send and a hand-built Message. Assert: the message reaches the DLQ after one attempt, and the next message is handled. Confirm both fail.
2. Fix: throw from deserialization. Make that exception non-retryable in the retry policy, or wrap it in AmqpRejectAndDontRequeueException; check the Spring AMQP 4.1 docs for the idiomatic option and say which you chose and why in the commit body.
3. All green.
```

#### Prompt 2.9: Close step 2

```text
Follow the plan conventions. Spec section: "Step 2", "Done when". Branch fix/e03-broker-bugs.

1. Run `./gradlew clean build -Pci=true` in solved and exercises. Every new test passes, the RabbitMQ propagation test runs without @Disabled, and the step 1 counts hold plus the new tests.
2. Scan the test output for stack traces or error logs that no test asserts. Fix them or capture them.
3. Push and open a PR titled "Fix messaging bugs in the e03 Kafka and RabbitMQ sagas". List B1–B5 with the test that proves each one.
4. Tick 2.9 and stop for Oskar's merge.
```

### Step 3: Idiomatic e03 adapters (branch `refactor/e03-idiomatic-adapters`)

#### Prompt 3.1: In-memory scenarios become transport-agnostic

```text
Follow the plan conventions. Spec section: "Step 3a". Create branch refactor/e03-idiomatic-adapters off the updated main.

Target: S/e03_businessprocesses/sagas/solution1_aggregates/BusinessProcessTests and S/e03_businessprocesses/sagas/solution2_immutableentities/BusinessProcessTests.

1. Turn each into an abstract class, for example `abstract class BusinessProcessScenarios`, with the scenario @Test methods unchanged in meaning. It gets the facades and the MessageCatcher through abstract methods or protected fields, set up by a subclass.
2. Wrap every assertion that reads published messages in Awaitility's await().untilAsserted(...). It passes immediately on the in-memory bus. Use a single shared timeout constant.
3. Add an InMemoryBusinessProcessTests subclass per flavour that does today's @BeforeEach wiring: new InMemoryMessageBus(), the facades, configureGroupCheckouts/configureGuestStayAccounts.
4. Make sure Awaitility is a testImplementation dependency (BOM-managed, no version).

Verify: the in-memory test count per flavour is unchanged, all green, and the run time hasn't grown noticeably.

Commit: "refactor(eda-workshop): make the saga scenarios independent of the transport".
```

#### Prompt 3.2: The Kafka run reuses the solution1 scenarios

```text
Follow the plan conventions. Spec section: "Step 3a". Branch refactor/e03-idiomatic-adapters.

1. Create S/e03_businessprocesses/sagas_kafka/KafkaAggregatesBusinessProcessTests extending sagas/solution1_aggregates/BusinessProcessScenarios. It's a Spring Boot test that:
   - provides KafkaMessageBus;
   - builds the facades from the sagas/solution1_aggregates package, not from the sagas_kafka copy;
   - calls configureGroupCheckouts/configureGuestStayAccounts once;
   - registers the MessageCatcher as middleware.
   Keep the existing @ComponentScan/@Import style for now; 3.15 replaces it.
2. Move GroupCheckoutMessageTypeMapping (and any other Kafka-specific registration for solution1) so it maps the sagas/solution1_aggregates types. Place it in the sagas_kafka test wiring package.
3. Repoint MessageMetadataPropagationTest and KafkaErrorHandlingTest to the sagas/solution1_aggregates types.
4. Delete sagas_kafka/solution1_aggregates entirely: domain, facades, configs, its BusinessProcessTests and EntityDefinitionTests (the in-memory copies already cover the domain unit tests).
5. Delete sagas_kafka/core/MessageCatcher if the shared core/MessageCatcher now covers it.

Verify: the solution1 scenarios run on in-memory and Kafka, and all Kafka tests are green. BrokerIsolationTests is still green.

Commit: "refactor(eda-workshop): run the aggregate saga scenarios on Kafka from one source".
```

#### Prompt 3.3: The Kafka run reuses the solution2 scenarios

```text
Follow the plan conventions. Spec section: "Step 3a". Branch refactor/e03-idiomatic-adapters.

Repeat 3.2 for solution2_immutableentities:
- create KafkaImmutableEntitiesBusinessProcessTests extending the solution2 scenarios;
- move the type mapping;
- delete sagas_kafka/solution2_immutableentities.

After this, if sagas_kafka/core/SagaResult has no users, delete it.

Verify: all green, with each scenario now run twice per flavour (in-memory and Kafka).

Commit: "refactor(eda-workshop): run the decider saga scenarios on Kafka from one source".
```

#### Prompt 3.4: The RabbitMQ run reuses both scenario suites

```text
Follow the plan conventions. Spec section: "Step 3a", "Done when". Branch refactor/e03-idiomatic-adapters.

Repeat 3.2 and 3.3 for sagas_rabbitmq:
- create RabbitMQAggregatesBusinessProcessTests and RabbitMQImmutableEntitiesBusinessProcessTests;
- move the type mappings;
- repoint the propagation and error-handling tests;
- delete sagas_rabbitmq/solution1_aggregates, sagas_rabbitmq/solution2_immutableentities, and the RabbitMQ SagaResult and MessageCatcher copies if unused.

Done when: each scenario exists in one source file and runs three times (in-memory, Kafka, RabbitMQ) per flavour, and the whole of solved is green.

Commit: "refactor(eda-workshop): run the saga scenarios on RabbitMQ from one source".
```

#### Prompt 3.5: Messages are routed by convention, with optional registrations

```text
Follow the plan conventions. Spec section: "Step 3b", item 1. Branch refactor/e03-idiomatic-adapters.

1. Tests first, in e03 core (MessageRouterTest), for a MessageRouter that resolves a RoutingTarget(target, key) per message:
   - a message without a registration follows the convention;
   - MessageRoutes registers route(Class, message -> RoutingTarget), routeTarget(Class, function), routeTarget(inPackage("..."), target) and routeKey(Class, function). A class registration also covers its subtypes; inPackage covers the package and its subpackages;
   - target and key resolve independently: each comes from the first matching registration that provides it, otherwise from the convention. A function returning null falls back to the convention.
   Confirm they fail, then implement RoutingTarget, MessageRoutes and MessageRouter.
2. No registration is required. Conventions:
   - Kafka: target = app.kafka.topic, key = the first record component (null for non-records and records without components);
   - RabbitMQ: target = app.rabbitmq.exchange, routing key = the message class name.
3. Each bus collects the MessageRoutes beans through ObjectProvider and builds its own MessageRouter with its convention. No core bean.
4. Integration tests per broker (KafkaMessageRoutingTest, RabbitMQMessageRoutingTest): the conventional key, a registered key, a registered target with a registered key, and a registered target with the conventional key. Kafka reads raw records with a plain consumer; RabbitMQ binds a queue to the expected exchange and routing key.
5. A registered target needs that topic or exchange declared and consumed. The e03 listeners read only the configured topic and queue.

Commit: "feat(eda-workshop): route messages by convention with optional registrations".
```

#### Prompt 3.6: The Kafka publish waits with a bounded timeout

```text
Follow the plan conventions. Spec section: "Step 3b", item 2, and decision 8. Branch refactor/e03-idiomatic-adapters.

1. Test first:
   - publish a message the broker must reject. A deterministic option: a payload larger than the producer's max.request.size, set low for this test. Assert that publish throws the Kafka exception itself, not a RuntimeException wrapper, within the configured timeout;
   - a second test: when the thread is interrupted while waiting, publish throws and Thread.currentThread().isInterrupted() is true afterwards.
2. Change KafkaMessageBus.publish:
   - get(timeout) with a configurable timeout;
   - unwrap ExecutionException to its cause and rethrow it (unchecked, as is, or wrapped in KafkaException only if the cause is checked);
   - on InterruptedException, restore the interrupt flag before throwing.
3. All green.

Commit: "fix(eda-workshop): bound the Kafka publish wait and surface the real failure".
```

#### Prompt 3.7: RabbitMQ publisher confirms and routing keys

```text
Follow the plan conventions. Spec section: "Step 3b", item 3. Branch refactor/e03-idiomatic-adapters.

1. Tests first:
   - publishing to an exchange that doesn't exist makes publish throw within the timeout. Without confirms it silently succeeds today; confirm the test fails;
   - the routing key of a published message equals its type name. Bind a test queue with that exact key and assert the message arrives.
2. Set spring.rabbitmq.publisher-confirm-type=correlated. In RabbitMQMessageBus.publish, send with CorrelationData and wait for its confirm with a bounded timeout; a nack, a timeout or a returned message throws. Check the Spring AMQP 4.1 docs on CorrelationData.getFuture().
3. Routing key = the message type name. Remove the hard-coded "groupcheckout" segment in buildRoutingKey. Update the bindings so the existing scenarios still route.

All green.

Commit: "feat(eda-workshop): confirm RabbitMQ publishes and route by message type".
```

#### Prompt 3.8: Drop the manual spans

```text
Follow the plan conventions. Spec section: "Step 3b", item 4. Branch refactor/e03-idiomatic-adapters.

1. Before changing anything, check how the propagation tests read traces. If they assert on the "messagebus.publish" or "messagebus.handle" spans by name, change them to assert on the template and listener observations instead. Turn those on now with:
   - spring.kafka.template.observation-enabled
   - spring.kafka.listener.observation-enabled
   - spring.rabbitmq.template.observation-enabled
   - spring.rabbitmq.listener.simple.observation-enabled
   If the variants still build their own templates or factories, set observation on those for now, and leave a todo.md note for 3.11 and 3.12.
2. Run the propagation tests; they must pass against the built-in observations.
3. Remove the manual span code from KafkaMessageBus and RabbitMQMessageBus.

All green.

Commit: "refactor(eda-workshop): rely on Spring's messaging observations instead of manual spans".
```

#### Prompt 3.9: Kafka versioning moves into a deserializer

```text
Follow the plan conventions. Spec section: "Step 3c", items 1, 3, 4. Branch refactor/e03-idiomatic-adapters.

1. Unit test first, with no broker: a VersionedMessageDeserializer implements Deserializer<Object>.
   - Given headers naming a registered type plus a JSON body, it returns the typed object.
   - Given an old-version body that has a registered transformation, it returns the transformed type.
   - Given an unknown type, it throws.
   Reuse the transformation cases the current versioning tests cover.
2. Implement it: try MessageTransformations first, then delegate to Spring Kafka's Jackson 3 JSON deserializer.
3. Configure the consumer value deserializer as ErrorHandlingDeserializer delegating to VersionedMessageDeserializer, through the Kafka consumer properties. Remove StringDeserializer for values.
4. The listener receives the typed payload and dispatches to subscribers. Delete TransformingMessageDeserializer and the string-parsing code in the listener.
5. Make sure deserialization failures still reach the DLT without retry: the 2.7 tests must still pass, now through ErrorHandlingDeserializer's DeserializationException.
6. Change the subscriber registry to ConcurrentHashMap.

All green.

Commit: "refactor(eda-workshop): deserialize and upcast Kafka messages in a Deserializer".
```

#### Prompt 3.10: RabbitMQ versioning moves into a MessageConverter

```text
Follow the plan conventions. Spec section: "Step 3c", items 2, 3, 4. Branch refactor/e03-idiomatic-adapters.

1. Unit test first, with no broker: a VersionedMessageConverter implements MessageConverter, with the same three cases as 3.9, using the AMQP Message and MessageProperties.
2. Implement it: transformations first, then delegate to Spring AMQP's Jackson 3 message converter. Delegate toMessage as well.
3. The @RabbitListener method takes the payload (Object), not a raw Message. Delete the RabbitMQ TransformingMessageDeserializer.
4. The 2.8 tests (unreadable messages go to the DLQ without retry) must still pass.
5. Change the subscriber registry to ConcurrentHashMap.

All green.

Commit: "refactor(eda-workshop): convert and upcast RabbitMQ messages in a MessageConverter".
```

#### Prompt 3.11: Kafka configuration through Boot

```text
Follow the plan conventions. Spec section: "Step 3d", items 1, 3, 4, 5 (Kafka part). Branch refactor/e03-idiomatic-adapters.

1. Delete the custom KafkaTemplate and ConcurrentKafkaListenerContainerFactory beans in KafkaConfiguration. Boot's auto-configured ones, plus properties, replace them:
   - observation-enabled flags;
   - key and value serializers;
   - the VersionedMessageDeserializer from 3.9.
   Set the value serializer in exactly one place, application properties or the test properties, and delete the DefaultKafkaProducerFactoryCustomizer that casts and replaces it.
2. Boot auto-wires a single CommonErrorHandler bean into its factory. Check that the DefaultErrorHandler from 2.5 is picked up, and keep the 2.5 and 2.7 tests green.
3. Replace every @Value("${app.kafka...}") with a @ConfigurationProperties("app.kafka") record (topic, groupId, publishTimeout). Register it with @EnableConfigurationProperties or @ConfigurationPropertiesScan.
4. Constructor injection everywhere in sagas_kafka. Replace System.out, System.err and printStackTrace with SLF4J.

All green, clean output.

Commit: "refactor(eda-workshop): configure the Kafka adapter through Spring Boot".
```

#### Prompt 3.12: RabbitMQ configuration through Boot

```text
Follow the plan conventions. Spec section: "Step 3d", items 1, 2, 3, 5 (RabbitMQ part). Branch refactor/e03-idiomatic-adapters.

1. Delete the custom RabbitTemplate and SimpleRabbitListenerContainerFactory beans. Declare only the VersionedMessageConverter bean; Boot attaches it to both the template and the listener factory.
   - Move the retry settings to spring.rabbitmq.listener.simple.retry.*. If 2.6 left a retry interceptor on the factory, remove it.
   - Keep the RejectAndDontRequeueRecoverer working; the 2.6 and 2.8 tests stay green.
2. Declare the topology once as Queue, TopicExchange and Binding beans, dead-lettering included. The listener uses @RabbitListener(queues = ...). Remove the duplicate @QueueBinding declaration.
3. Replace @Value("${app.rabbitmq...}") with a @ConfigurationProperties("app.rabbitmq") record.
4. Constructor injection and SLF4J everywhere in sagas_rabbitmq.

All green, clean output.

Commit: "refactor(eda-workshop): configure the RabbitMQ adapter through Spring Boot".
```

#### Prompt 3.13: Metadata headers, Kafka

```text
Follow the plan conventions. Spec section: "Step 3e". Branch refactor/e03-idiomatic-adapters.

1. Investigate first, and write the findings in todo.md under "3e findings": with management.tracing.baggage.remote-fields=correlationId,causationId,entityId,entityVersion, which of these does Boot 4 already propagate across Kafka records, with no custom code? Find out by temporarily disabling KafkaMessageBaggageHandler and running MessageMetadataPropagationTest.
2. Test first: published Kafka records carry messageId, correlationId and causationId headers. Assert on a raw consumed record.
3. Keep custom code only for what Boot doesn't do. Expected: generating messageId, and mapping the incoming messageId to causationId. Delete the rest of KafkaMessageBaggageHandler.
4. MessageMetadata becomes a final class with a private constructor. Kafka-specific helpers live in sagas_kafka.

The propagation test and everything else stay green.

Commit: "refactor(eda-workshop): carry message metadata as Kafka headers, leaning on baggage propagation".
```

#### Prompt 3.14: Metadata headers, RabbitMQ

```text
Follow the plan conventions. Spec section: "Step 3e". Branch refactor/e03-idiomatic-adapters.

Repeat 3.13 for RabbitMQ:
- investigate what remote-fields covers over AMQP and add it to the findings;
- test first that the headers are on the raw message;
- trim RabbitMessageBaggageHandler to what Boot doesn't do;
- RabbitMQ-specific MessageMetadata helpers move into sagas_rabbitmq.

All green.

Commit: "refactor(eda-workshop): carry message metadata as RabbitMQ headers, leaning on baggage propagation".
```

#### Prompt 3.15: Test wiring cleanup

```text
Follow the plan conventions. Spec section: "Step 3f". Branch refactor/e03-idiomatic-adapters.

1. Delete S/e03_businessprocesses/sagas_kafka/core/KafkaPropertiesBootstrapTest.java.
2. Replace @ComponentScan on the Kafka and RabbitMQ test classes with one small @SpringBootConfiguration per variant: KafkaSagaTestApplication and RabbitMQSagaTestApplication, each with @EnableAutoConfiguration and the variant's beans. Each test class points to it with @SpringBootTest(classes = ...).
3. Remove the "messaging.type" property switch if nothing reads it anymore.
4. Check that TestApplicationConfig is still used. If not, delete it.

Same test count, all green.

Commit: "refactor(eda-workshop): give each broker variant its own test application".
```

#### Prompt 3.16: Java naming in SagaResult

```text
Follow the plan conventions. Spec section: "Step 3g". Branch refactor/e03-idiomatic-adapters.

In S/e03_businessprocesses/sagas/core/SagaResult, the only copy left after 3.3 and 3.4, rename:
- Send to send
- Publish to publish
- Ignore to IGNORE

Update every caller. exercises has no SagaResult, so there's nothing to change there; confirm with grep.

All green.

Commit: "refactor(eda-workshop): follow Java naming in SagaResult".
```

#### Prompt 3.17: Close step 3

```text
Follow the plan conventions. Spec section: "Step 3", "Done when". Branch refactor/e03-idiomatic-adapters.

1. Check by grep in S/e03_businessprocesses/sagas_kafka and sagas_rabbitmq. None of these should remain:
   - catch (Exception or catch (Throwable in listeners;
   - System.out, System.err, printStackTrace;
   - @Autowired on fields;
   - KafkaTemplate, ConcurrentKafkaListenerContainerFactory, RabbitTemplate or SimpleRabbitListenerContainerFactory built by hand.
2. Run `./gradlew clean build -Pci=true` in both projects. Everything is green with clean output; each scenario runs three times per flavour.
3. Push and open a PR titled "Make the e03 Kafka and RabbitMQ adapters idiomatic", grouped by 3a–3g, with the 3e findings from todo.md.
4. Tick 3.17 and stop for Oskar's merge.
```

### Step 4: `e04_spring_kafka` (branch `feat/e04-spring-kafka`)

#### Prompt 4.1: Kafka test infrastructure and topology

```text
Follow the plan conventions. Spec section: "Step 4". Create branch feat/e04-spring-kafka off the updated main.

Work in S/e04_spring_kafka/. This prompt builds the shared core and the topology only.

1. S/e04_spring_kafka/core/:
   - KafkaContainers: a @TestConfiguration with a ConfluentKafkaContainer bean and @ServiceConnection;
   - MessageCatcher: a test-only @KafkaListener on all four topics, collecting payloads, with an await-based shouldReceiveMessages(...);
   - JSON serialization configuration: Spring Kafka's Jackson 3 serializer and deserializer, type headers, and trusted packages limited to e04_spring_kafka.
   This is the part attendees are given (Q7c).
2. Test first, TopologyTests: with an AdminClient, assert the topics exist with 3 partitions each, plus their .DLT topics:
   - guest-stay-account-events
   - guest-stay-account-commands
   - group-checkout-events
   - group-checkout-commands
3. S/e04_spring_kafka/solution1_aggregates/: an E04KafkaApplication @SpringBootConfiguration with @EnableAutoConfiguration, and a Topology class with the NewTopic beans. Constants for the topic names live there.

Green.

Commit: "feat(eda-workshop): add Kafka topology for the e04 Spring Kafka exercise".
```

#### Prompt 4.2: Facades send events with KafkaTemplate

```text
Follow the plan conventions. Spec section: "Step 4", topology table and the dual-write paragraph. Branch feat/e04-spring-kafka.

1. Copy the e03 domain for solution1_aggregates into S/e04_spring_kafka/solution1_aggregates/:
   - entities, events and commands;
   - the facades;
   - GroupCheckoutSaga;
   - the sagas/core SagaResult, placed in e04_spring_kafka/core.
   Remove every MessageBus reference. Storage stays the in-memory Database from e03 core; reuse that class directly instead of copying it.
2. Test first: checking a guest out through GuestStayAccountFacade puts GuestCheckedOut on guest-stay-account-events with key = the guest stay account ID. Initiating a group checkout puts GroupCheckoutInitiated on group-checkout-events with key = the group checkout ID. Assert through the MessageCatcher, and the key through a raw consumer.
3. The facades take KafkaTemplate<String, Object> through the constructor and send after storing. Wait for the send result with a bounded timeout, as decision 8 does for e03.
4. In each solved facade, add one short comment at the store-then-send point naming the dual write. If the send fails, or the process dies between the store and the send, the state is saved, no event goes out, and the group checkout never continues. Point to e06_spring_modulith.

Green.

Commit: "feat(eda-workshop): publish e04 events with KafkaTemplate".
```

#### Prompt 4.3: Command listeners

```text
Follow the plan conventions. Spec section: "Step 4", "What attendees build" item 4. Branch feat/e04-spring-kafka.

1. Tests first:
   - sending CheckOutGuest to guest-stay-account-commands, keyed by guest stay account ID, for a checked-in guest produces GuestCheckedOut on guest-stay-account-events;
   - sending RecordGuestCheckoutCompletion to group-checkout-commands updates the group checkout and produces its event.
2. GuestStayAccountCommandListener: @KafkaListener(topics = guest-stay-account-commands, groupId = "guest-stay-accounts") that calls the facade.
   GroupCheckoutCommandListener: the same on group-checkout-commands with groupId = "group-checkouts", one @KafkaHandler per command type.

Green.

Commit: "feat(eda-workshop): handle e04 commands with Kafka listeners".
```

#### Prompt 4.4: The saga listener and the e03 scenarios

```text
Follow the plan conventions. Spec section: "Step 4", "What attendees get" item 5 and "What attendees build" item 3. Branch feat/e04-spring-kafka.

1. Tests first: copy the scenarios from S/e03_businessprocesses/sagas/solution1_aggregates/BusinessProcessScenarios into S/e04_spring_kafka/solution1_aggregates/BusinessProcessTests. Adapt them to Kafka: a Spring Boot test, Awaitility, and the MessageCatcher from 4.1. The group checkout scenarios fail because nothing reacts to events yet.
2. GroupCheckoutSagaListener: a class-level @KafkaListener(topics = {guest-stay-account-events, group-checkout-events}, groupId = "group-checkouts"), with one @KafkaHandler per event the saga handles.
   - Each handler calls GroupCheckoutSaga.handle(event) and sends the resulting commands with KafkaTemplate to the receiver's command topic, keyed by the receiver's entity ID.
   - Add a @KafkaHandler(isDefault = true) that ignores events the saga doesn't care about.
3. All scenarios green.

Commit: "feat(eda-workshop): run the group checkout saga as a Spring Kafka listener".
```

#### Prompt 4.5: Dead-letter topics

```text
Follow the plan conventions. Spec section: "Step 4", "What attendees get" item 6 and "What attendees build" item 5. Branch feat/e04-spring-kafka.

1. Test first, DeadLetterTests: send a poison record to guest-stay-account-commands, for example CheckOutGuest for an unknown account, so the facade throws. Assert:
   - it lands on guest-stay-account-commands.DLT after a bounded number of attempts;
   - a healthy command sent next is processed;
   - the error log is captured and asserted.
   Also cover a record that can't be deserialized: it lands on the DLT with no retry.
2. A DefaultErrorHandler bean with DeadLetterPublishingRecoverer and FixedBackOff. Deserialization errors are not retryable; configure ErrorHandlingDeserializer in the given serialization setup if 4.1 didn't.

Green, clean output.

Commit: "feat(eda-workshop): dead-letter failed e04 Kafka records".
```

#### Prompt 4.6: Deciders flavour, facades and command listeners

```text
Follow the plan conventions. Spec section: "Step 4", Q5. Branch feat/e04-spring-kafka.

Repeat 4.2 and 4.3 for S/e04_spring_kafka/solution2_immutableentities/:
- copy the e03 sagas/solution2_immutableentities domain without MessageBus;
- facades send with KafkaTemplate, with the dual-write comment;
- add the command listeners.

Reuse core/ (containers, catcher, serialization) and the Topology from solution1, moving Topology to core/ if both flavours need it. Each flavour has its own @SpringBootConfiguration and its own consumer-group suffix, so the two flavours' tests don't steal each other's records when they share a container.

Tests first, mirroring 4.2 and 4.3. Green.

Commit: "feat(eda-workshop): publish and handle e04 decider messages over Kafka".
```

#### Prompt 4.7: Deciders flavour, saga listener, scenarios and DLT

```text
Follow the plan conventions. Spec section: "Step 4". Branch feat/e04-spring-kafka.

Repeat 4.4 and 4.5 for solution2_immutableentities:
- the scenarios as tests first;
- the saga listener with @KafkaHandler;
- the DLT test.
Reuse the error handler bean if it can live in core/.

Done when: both flavours are green in solved and the whole of solved builds with -Pci=true.

Commit: "feat(eda-workshop): run the e04 decider saga as a Spring Kafka listener".
```

#### Prompt 4.8: Derive the exercise for the aggregates flavour

```text
Follow the plan conventions. Spec section: "Step 4", "What attendees get" and "What attendees build". Branch feat/e04-spring-kafka.

1. In exercises/build.gradle, add:
   - spring-boot-starter-kafka;
   - testcontainers-kafka;
   - testcontainers-junit-jupiter;
   - spring-boot-testcontainers;
   - awaitility.
   All are BOM-managed, so no versions.
2. Copy S/e04_spring_kafka/core and solution1_aggregates to X/e04_spring_kafka/. Package names change from solved to exercises paths where they differ.
3. Remove what attendees build, leaving a TODO comment that says what goes there:
   - the Topology NewTopic beans: keep the class and the topic-name constants;
   - the KafkaTemplate sends in the facades: keep a TODO after the store;
   - the command listeners and the saga listener: keep empty classes with a TODO;
   - the DefaultErrorHandler bean.
   Drop the dual-write comment (Q7b).
4. Keep everything listed under "What attendees get": domain, saga function, storage, serialization, containers, catcher, tests.
5. Tag every test class in X/e04_spring_kafka with @Tag("Exercise").
6. Verify:
   - exercises compiles;
   - `./gradlew test --tests '*e04_spring_kafka*'` fails, and only because of the TODOs. Read each failure;
   - `./gradlew build -Pci=true` is green.

Commit: "feat(eda-workshop): add the e04 Spring Kafka exercise for aggregates".
```

#### Prompt 4.9: Derive the deciders exercise, write the README, close step 4

```text
Follow the plan conventions. Spec section: "Step 4", "Done when". Branch feat/e04-spring-kafka.

1. Repeat 4.8 for solution2_immutableentities.
2. Write X/e04_spring_kafka/README.md in the style of X/e03_businessprocesses/README.md. Cover:
   - the goal;
   - the four topics, and the rule that events go on the publisher's topic and commands on the receiver's;
   - why records are keyed by entity ID;
   - consumer groups per module;
   - what's given;
   - what to build, as a numbered list;
   - how to run the tests (docker compose up -d, Testcontainers).
   Don't mention the dual write.
3. Run `./gradlew clean build -Pci=true` in both projects. Push and open a PR titled "Add the e04 Spring Kafka exercise".
4. Tick 4.9 and stop for Oskar's merge.
```

### Step 5: `e04_spring_rabbitmq` (branch `feat/e04-spring-rabbitmq`)

#### Prompt 5.1: RabbitMQ test infrastructure and topology

```text
Follow the plan conventions. Spec section: "Step 5", topology table. Create branch feat/e04-spring-rabbitmq off the updated main.

Work in S/e04_spring_rabbitmq/.

1. core/:
   - RabbitMQContainers with @ServiceConnection;
   - a test-only MessageCatcher, a @RabbitListener on its own auto-delete queues bound to all four exchanges with "#" or the matching keys;
   - JSON serialization: the Jackson 3 message converter bean with trusted packages (given to attendees).
2. Test first, TopologyTests: through RabbitAdmin / AmqpAdmin, assert:
   - the four exchanges exist with the right type: guest-stay-accounts.events (topic), guest-stay-accounts.commands (direct), group-checkouts.events (topic), group-checkouts.commands (direct);
   - the queues and bindings from the spec table exist;
   - every queue has x-dead-letter-exchange=dlx, and the dlx exchange has one DLQ per queue.
   Use the management API through the container's HTTP URL if AmqpAdmin can't read exchange types; check the Testcontainers 2 RabbitMQContainer docs.
3. solution1_aggregates/: an E04RabbitMQApplication @SpringBootConfiguration and a Topology class with the beans.

Green.

Commit: "feat(eda-workshop): add RabbitMQ topology for the e04 Spring AMQP exercise".
```

#### Prompt 5.2: Facades send events with RabbitTemplate

```text
Follow the plan conventions. Spec section: "Step 5". Branch feat/e04-spring-rabbitmq.

As 4.2, but with RabbitTemplate.convertAndSend(exchange, routingKey = event type simple name, event).
- Turn publisher confirms on (spring.rabbitmq.publisher-confirm-type=correlated) and wait for the confirm with a bounded timeout, as in e03.
- Copy the domain without MessageBus.
- Add the dual-write comment in the solved facades, pointing to e06.

Tests first: the events arrive at the catcher with the right routing key.

Commit: "feat(eda-workshop): publish e04 events with RabbitTemplate".
```

#### Prompt 5.3: Command listeners

```text
Follow the plan conventions. Spec section: "Step 5", "What attendees build" item 4. Branch feat/e04-spring-rabbitmq.

As 4.3:
- a @RabbitListener(queues = "guest-stay-accounts.commands") calling the facade;
- a class-level @RabbitListener(queues = "group-checkouts.commands") with one @RabbitHandler per command.

Tests first: commands sent to the command exchanges produce the expected events.

Commit: "feat(eda-workshop): handle e04 commands with RabbitMQ listeners".
```

#### Prompt 5.4: The saga listener and the e03 scenarios

```text
Follow the plan conventions. Spec section: "Step 5", "What attendees build" item 3. Branch feat/e04-spring-rabbitmq.

As 4.4:
1. Copy the scenarios as tests first.
2. Add a class-level @RabbitListener on the two group-checkouts.* event queues, with one @RabbitHandler per saga event and an isDefault handler that ignores the rest.
3. Each handler calls GroupCheckoutSaga.handle and sends the commands with convertAndSend to the receiver's command exchange.

All scenarios green.

Commit: "feat(eda-workshop): run the group checkout saga as a Spring AMQP listener".
```

#### Prompt 5.5: Retry and dead-lettering

```text
Follow the plan conventions. Spec section: "Step 5", "What attendees build" item 5. Branch feat/e04-spring-rabbitmq.

As 4.5:
1. Test first: a poison command ends in its DLQ after bounded retries, the next one is processed, and the logs are captured and asserted. An unconvertible message lands in the DLQ with no retry.
2. Configure spring.rabbitmq.listener.simple.retry.* and add a RejectAndDontRequeueRecoverer bean. Check the Boot 4.1 docs for how the recoverer is picked up.

Green, clean output.

Commit: "feat(eda-workshop): retry and dead-letter failed e04 RabbitMQ messages".
```

#### Prompt 5.6: Deciders flavour, facades and command listeners

```text
Follow the plan conventions. Spec section: "Step 5", Q5. Branch feat/e04-spring-rabbitmq.

Repeat 5.2 and 5.3 for solution2_immutableentities, tests first. Share core/ and the Topology. Give each flavour its own queues: suffix the queue names per flavour, or declare them per flavour configuration, so the two flavours' tests don't consume each other's messages on a shared broker.

Commit: "feat(eda-workshop): publish and handle e04 decider messages over RabbitMQ".
```

#### Prompt 5.7: Deciders flavour, saga, scenarios and DLQ

```text
Follow the plan conventions. Spec section: "Step 5". Branch feat/e04-spring-rabbitmq.

Repeat 5.4 and 5.5 for solution2_immutableentities, tests first. Both flavours are green, and solved builds with -Pci=true.

Commit: "feat(eda-workshop): run the e04 decider saga as a Spring AMQP listener".
```

#### Prompt 5.8: Derive the exercises, write the README, close step 5

```text
Follow the plan conventions. Spec section: "Step 5", "Done when". Branch feat/e04-spring-rabbitmq.

1. In exercises/build.gradle, add spring-boot-starter-amqp and testcontainers-rabbitmq.
2. Derive X/e04_spring_rabbitmq for both flavours, exactly as in 4.8:
   - remove the topology beans, sends, listeners and recoverer, leaving TODOs;
   - keep the domain, the saga function, serialization, containers, the catcher and the tests;
   - tag the tests @Tag("Exercise");
   - drop the dual-write comment.
3. Write X/e04_spring_rabbitmq/README.md with the same structure as the Kafka one, plus a short section contrasting the ownership rule as exchanges and queues with Kafka's topics: who declares what, and where routing happens.
4. Verify: exercises compiles, the e04 RabbitMQ tests fail only at the TODOs, and -Pci=true is green in both projects.
5. Push, open a PR titled "Add the e04 Spring AMQP exercise", tick 5.8 and stop for Oskar's merge.
```

### Step 7: `e06_spring_modulith` (branch `feat/e06-spring-modulith`)

#### Prompt 7.1: Dependencies, containers and module boundaries

```text
Follow the plan conventions. Spec section: "Step 7", items 1 and 6 (verify). Create branch feat/e06-spring-modulith off the updated main.

Before anything else: check todo.md for the confirmation of decision D-e06 (plan.md section 1). If it isn't ticked, stop and ask Oskar.

1. In solved/build.gradle, add:
   - the Spring Modulith 2.1 BOM (mavenBom in dependencyManagement);
   - spring-modulith-starter-core, spring-modulith-starter-jpa, spring-modulith-events-kafka, spring-modulith-events-api;
   - spring-boot-starter-data-jpa;
   - testcontainers-postgresql;
   - spring-modulith-starter-test.
   Don't add the AMQP externalizer: both externalizers would be active, and every event would go to both brokers.
2. S/e06_spring_modulith/core/: a @TestConfiguration with a PostgreSQLContainer and a ConfluentKafkaContainer, both with @ServiceConnection. Check whether src/test/resources/schema-postgres.sql or spring.datasource.* in application.properties interfere. If they do, override them in this exercise's test properties rather than editing shared files.
3. S/e06_spring_modulith/solution1_aggregates/: an E06Application @SpringBootApplication. Under it, the packages gueststayaccounts and groupcheckouts, each with a package-info.java. Modulith treats each one as a module.
4. Test first, ModuleStructureTests: ApplicationModules.of(E06Application.class).verify() passes, and the two expected modules are detected.

Green.

Commit: "feat(eda-workshop): set up the e06 Spring Modulith exercise".
```

#### Prompt 7.2: Guest stay accounts persist through JPA

```text
Follow the plan conventions. Spec section: "Step 7", item 2 (aggregates). Branch feat/e06-spring-modulith.

1. Test first: check a guest in and out through GuestStayAccountFacade. A fresh read through the repository shows the status. Starting from a stale entity fails with an optimistic-locking exception.
2. Copy the e03 sagas/solution1_aggregates GuestStayAccount into gueststayaccounts and make it a JPA entity: @Entity, @Id, @Version. Keep its behaviour methods; add only the no-arg constructor and mapping JPA needs.
   - GuestStayAccountRepository extends JpaRepository.
   - GuestStayAccountFacade is @Transactional and uses the repository.
   - Schema from Hibernate ddl-auto=create-drop in this exercise's test properties.
3. Nothing is published yet.

Green.

Commit: "feat(eda-workshop): persist e06 guest stay accounts with Spring Data JPA".
```

#### Prompt 7.3: Externalized guest stay account events (registry mode)

```text
Follow the plan conventions. Spec section: "Step 7", items 3 and 5 (first mode). Branch feat/e06-spring-modulith.

1. Test first: checking a guest out puts GuestCheckedOut on guest-stay-account-events with key = the guest stay account ID, read with a raw consumer. Afterwards the event publication registry has no incomplete publications (IncompleteEventPublications or the EventPublicationRepository).
2. GuestStayAccountFacade publishes its events with ApplicationEventPublisher inside the transaction. The event records get @Externalized("guest-stay-account-events::#{guestStayAccountId()}").
3. Add the Kafka JSON serialization settings Modulith's externalizer uses. Check the Modulith 2.1 docs for how it serializes and which headers it sets.
4. Test: if the transaction rolls back, nothing is published and nothing reaches Kafka.

Green.

Commit: "feat(eda-workshop): externalize e06 guest stay account events to Kafka".
```

#### Prompt 7.4: Group checkouts, the saga and end-to-end scenarios

```text
Follow the plan conventions. Spec section: "Step 7", items 2 and 4, plus decision D-e06. Branch feat/e06-spring-modulith.

1. Tests first: copy the e03 scenarios into S/e06_spring_modulith/solution1_aggregates/BusinessProcessTests as Spring Boot tests with Awaitility. Assert on what reaches Kafka and on the repository state.
2. groupcheckouts:
   - GroupCheckout as a JPA entity; the guest stay statuses go in an @ElementCollection or a JSON column, whichever keeps the e03 behaviour methods intact;
   - a repository and a @Transactional facade that publishes through ApplicationEventPublisher;
   - group checkout events @Externalized to group-checkout-events, keyed by group checkout ID.
3. The saga, as decision D-e06 says:
   - GroupCheckoutInitiated: an @ApplicationModuleListener in groupcheckouts runs GroupCheckoutSaga and sends each CheckOutGuest command to guest-stay-account-commands, keyed by guest stay account ID. The command record carries @Externalized and goes through ApplicationEventPublisher, the only entry point Modulith's outbox has. It's still a command; name variables and methods accordingly (send, command), not "event";
   - guest stay accounts: a @KafkaListener on guest-stay-account-commands calls their facade;
   - group checkouts: a @KafkaListener on guest-stay-account-events runs the saga for GuestCheckedOut and GuestCheckoutFailed and calls its own facade.
   Modules don't call each other's code; ApplicationModules.verify() stays green.
4. All scenarios green.

Commit: "feat(eda-workshop): run the e06 group checkout over Kafka through the outbox".
```

#### Prompt 7.5: Broker down at commit (registry mode)

```text
Follow the plan conventions. Spec section: "Step 7", items 5 (first mode) and 6 (failure case). Branch feat/e06-spring-modulith.

1. Test first, BrokerOutageTests:
   - pause the Kafka container through the Testcontainers Docker client;
   - check a guest out: the transaction commits and the state is saved;
   - assert the publication is incomplete in the registry;
   - unpause Kafka and resubmit incomplete publications (IncompleteEventPublications.resubmitIncompletePublications, or what Modulith 2.1 offers);
   - assert the event reaches guest-stay-account-events.
   Keep the producer's max.block.ms and delivery.timeout.ms low in this test so it doesn't wait for minutes.
2. Make it pass with configuration only, if possible. Capture and assert the expected producer error logs.
3. Note in todo.md what this mode needs by hand (the resubmission), because the README uses it in 7.8.

Commit: "test(eda-workshop): show e06 events surviving a broker outage via the publication registry".
```

#### Prompt 7.6: A real outbox

```text
Follow the plan conventions. Spec section: "Step 7", item 5 (second mode). Branch feat/e06-spring-modulith.

1. Read the Spring Modulith 2.1 docs on the Namastack Outbox and JobRunr integrations. Pick one using these criteria:
   - the fewest moving parts in tests;
   - automatic retry without the manual resubmission from 7.5;
   - ordering per key.
   Write the choice and the reason in todo.md under "7.6 decision".
2. Test first: a second BrokerOutageTests variant, under a Spring profile or its own test application, with the outbox mode on. It shows the event reaching Kafka after the outage with no manual resubmit.
3. Add the dependency and configuration for the chosen mode. Keep the registry mode as the default, so 7.5 still demonstrates its limits.

Both outage tests green.

Commit: "feat(eda-workshop): add an outbox-backed externalization mode to e06".
```

#### Prompt 7.7: Deciders flavour with JSON state

```text
Follow the plan conventions. Spec section: "Step 7", item 2 (deciders). Branch feat/e06-spring-modulith.

1. S/e06_spring_modulith/solution2_immutableentities/, with its own @SpringBootApplication and modules.
2. Tests first: the scenarios, ModuleStructureTests, and one optimistic-locking test.
3. For each decider stream, a small JPA entity holds:
   - the ID;
   - @Version long version;
   - the state as a JSON column (@JdbcTypeCode(SqlTypes.JSON)) holding the immutable state record.
   The facade loads the entity, runs the unchanged decider, stores the new state and publishes the events, all in one @Transactional method. Check that Hibernate's JSON mapping uses Jackson 3 and handles the sealed state hierarchy. If it needs @JsonTypeInfo on the state interface, add it and say so in the commit body.
4. Publishing, externalization and listeners as in 7.3 and 7.4.

All green.

Commit: "feat(eda-workshop): add the e06 decider flavour with JSON-stored state".
```

#### Prompt 7.8: Derive the exercise, write the README, close step 7

```text
Follow the plan conventions. Spec section: "Step 7", "Done when". Branch feat/e06-spring-modulith.

1. In exercises/build.gradle, add the same Modulith BOM and starters, data-jpa and testcontainers-postgresql.
2. Derive X/e06_spring_modulith for both flavours.
   - Given: entities, repositories, the facades' persistence code, the containers, ModuleStructureTests and the scenario tests.
   - Built by attendees, with TODOs left in place: publishing through ApplicationEventPublisher, the @Externalized annotations, the consuming @KafkaListeners and the module listener for the saga fan-out, and the body of the broker-outage test, leaving its name and a description of what it must prove.
   - Tag the tests @Tag("Exercise").
3. Write X/e06_spring_modulith/README.md:
   - the dual-write problem, pointing back to the solved e04 facades' comment;
   - the event publication registry;
   - commands through the outbox: Modulith calls every outgoing message an "event", but CheckOutGuest stays a command, sent to the receiver's topic. The API name describes the mechanism, not what the message means;
   - the two externalization modes. Quote the Modulith docs' line that the default "lacks critical features developers might expect from actual outbox pattern implementations". Say what 7.5 showed must be done by hand, and what the 7.6 choice adds;
   - module boundaries and verify();
   - what to build.
4. Verify: exercises compiles, the e06 tests fail only at the TODOs, and -Pci=true is green in both projects.
5. Push, open a PR titled "Add the e06 Spring Modulith exercise", tick 7.8 and stop for Oskar's merge.
```

### Step 8: Documentation and CI (branch `docs/messaging-exercises`)

#### Prompt 8.1: READMEs

```text
Follow the plan conventions. Spec section: "Step 8", items 1 and 2. Create branch docs/messaging-exercises off the updated main.

1. workshops/event-driven-architecture/README.md: after item 7, list e04 Spring Kafka, e04 Spring AMQP and e06 Spring Modulith. Give each one line on what it teaches, and note that it needs Docker. Leave a slot for e05; step 6 fills it.
2. X/e03_businessprocesses/README.md: add one line saying that the saga function is what the later exercises reuse, and only the wiring changes.

Commit: "docs(eda-workshop): list the Spring messaging exercises".
```

#### Prompt 8.2: CI check and close step 8

```text
Follow the plan conventions. Spec section: "Step 8", item 3. Branch docs/messaging-exercises.

1. Push the branch and watch both EDA workflows (`gh run watch`).
2. Record the duration of the test task in each, and of the Testcontainers-based test classes (from the JUnit XML in the uploaded reports, or from the log).
3. If the solved job takes over about 15 minutes or flakes, propose splitting the broker-backed tests into a separate job: show Oskar the workflow diff and wait for approval before changing CI.
4. Open a PR titled "Document the Spring messaging exercises", with the CI timings in the description. Tick 8.2 and stop for Oskar's merge.
```

### Step 6: `e05_spring_cloud_stream` (branch `feat/e05-spring-cloud-stream`, runs last)

#### Prompt 6.0: GA gate

```text
Follow the plan conventions. Spec section: "Step 6", prerequisite.

1. Check Maven Central (or https://spring.io/projects/spring-cloud) for the Spring Cloud release train that supports Spring Boot 4.1. It must be GA, not an RC or milestone. Record the train name, its Spring Cloud Stream version, and the date in todo.md.
2. If it isn't GA, stop here and tell Oskar. Don't continue to 6.1.
3. If it is GA, also check that the Kafka and Rabbit binders in that train build against Spring Kafka 4.1 and Spring AMQP 4.1, the versions the Boot BOM brings, and against Jackson 3.

Tick 6.0.
```

#### Prompt 6.1: Dependencies, two-binder test bases, and verified binder behaviour

```text
Follow the plan conventions. Spec section: "Step 6", items 1, 6, 7. Create branch feat/e05-spring-cloud-stream off the updated main.

1. In solved/build.gradle, add the Spring Cloud BOM for the train from 6.0, spring-cloud-stream-binder-kafka and spring-cloud-stream-binder-rabbit.
   - Make sure the existing e03, e04 and e06 tests still start; with two binders present, the other exercises shouldn't pick up Stream bindings. If they do, scope Stream with spring.cloud.stream.* properties only in e05's tests, or with a test configuration exclusion.
   - Run the full -Pci=true build before going further.
2. S/e05_spring_cloud_stream/core/:
   - KafkaBinderTestBase: a Kafka container with @ServiceConnection, and spring.cloud.stream.default-binder=kafka;
   - RabbitBinderTestBase: a RabbitMQ container with @ServiceConnection, and default-binder=rabbit.
3. Behaviour checks, as tests in BinderBehaviourTests, run on both bases:
   - a Function bean returning null sends nothing to its output binding;
   - a Function returning a List<Message<?>> sends each element as its own message;
   - StreamBridge.send(binding, binderName, payload) reaches the named binder.
   These are the spec's "verify by test, not assumption" items. If null doesn't behave as expected, stop and report: the saga's "ignore" path needs another design.

Green.

Commit: "feat(eda-workshop): set up Spring Cloud Stream on both binders for e05".
```

#### Prompt 6.2: Polymorphic events and StreamBridge in the facades

```text
Follow the plan conventions. Spec section: "Step 6", items 4 and 5. Branch feat/e05-spring-cloud-stream.

1. Copy the e03 solution1_aggregates domain into S/e05_spring_cloud_stream/solution1_aggregates, without MessageBus.
2. Tests first, on both binder bases: checking a guest out produces GuestCheckedOut on the guest-stay-account-events destination, and it deserializes back to the right subtype.
3. Add @JsonTypeInfo and @JsonSubTypes on the sealed GuestStayAccountEvent, GroupCheckoutEvent and the command interfaces.
4. Facades send through StreamBridge.send(bindingName, binderName, event). The binder name comes from configuration, so the same facade runs on both binders.

Green on both binders.

Commit: "feat(eda-workshop): publish e05 events through StreamBridge".
```

#### Prompt 6.3: Command consumers

```text
Follow the plan conventions. Spec section: "Step 6", item 3. Branch feat/e05-spring-cloud-stream.

1. Tests first, on both binders: commands sent to the command destinations produce the expected events.
2. Add Consumer<GuestStayAccountCommand> and Consumer<GroupCheckoutCommand> beans that call the facades.
   - Bind them in properties: spring.cloud.function.definition, plus the destination and group for each binding.
   - Use switch pattern matching on the sealed command type.

Green.

Commit: "feat(eda-workshop): handle e05 commands with Spring Cloud Stream consumers".
```

#### Prompt 6.4: Saga functions and scenarios on both binders

```text
Follow the plan conventions. Spec section: "Step 6", items 2 and 6. Branch feat/e05-spring-cloud-stream.

1. Tests first: copy the e03 scenarios into an abstract scenario class. Run it from KafkaBusinessProcessTests and RabbitBusinessProcessTests.
2. Saga as functions:
   - Function<GroupCheckoutEvent, List<Message<CheckOutGuest>>> for the fan-out from GroupCheckoutInitiated;
   - Function<GuestStayAccountEvent, GroupCheckoutCommand> returning null for ignored events. Rely on what 6.1 proved.
   - Set the partition or routing key per message as the binder needs: the Kafka message key and the Rabbit routing key, through message headers.
3. All scenarios green on both binders.

Commit: "feat(eda-workshop): run the group checkout saga as Spring Cloud Stream functions".
```

#### Prompt 6.5: Error handling on both binders

```text
Follow the plan conventions. Spec section: "Step 6", what attendees build: error handling. Branch feat/e05-spring-cloud-stream.

1. Test first, on both binders: a poison command is retried a bounded number of times, then lands in the binder's dead-letter destination; the next command is processed; the logs are captured and asserted.
2. Configure:
   - the consumer's max-attempts and back-off;
   - Kafka binder: enable-dlq and dlq-name;
   - Rabbit binder: auto-bind-dlq and republish-to-dlq.
   Check every property name in the Spring Cloud Stream docs for the 6.0 train.

Green.

Commit: "feat(eda-workshop): dead-letter failed e05 messages on both binders".
```

#### Prompt 6.6: Deciders flavour

```text
Follow the plan conventions. Spec section: "Step 6", Q5. Branch feat/e05-spring-cloud-stream.

Repeat 6.2 to 6.5 for solution2_immutableentities, tests first, sharing core/. Keep the two flavours' destinations and groups apart, so a shared container doesn't mix their messages.

Green on both binders.

Commit: "feat(eda-workshop): add the e05 decider flavour".
```

#### Prompt 6.7: Derive the exercise, write the docs, close step 6

```text
Follow the plan conventions. Spec section: "Step 6", "Done when", and "Step 8", item 1. Branch feat/e05-spring-cloud-stream.

1. exercises/build.gradle: add the Spring Cloud BOM and both binders.
2. Derive X/e05_spring_cloud_stream for both flavours.
   - Given: domain, saga, facades minus the sends, @JsonTypeInfo, containers and tests.
   - Built by attendees, with TODOs: the functions and consumers, the binding properties (with a commented skeleton), the StreamBridge sends, and the DLQ settings.
   - Tag the tests @Tag("Exercise").
3. X/e05_spring_cloud_stream/README.md: what to build, and a comparison with e04: what got shorter (no listener annotations, no topology beans) and what's now hidden (topology, keys, error handling in properties, the polymorphic typing trade-off).
4. Fill the e05 slot in workshops/event-driven-architecture/README.md.
5. Verify -Pci=true is green in both projects, push, and check the CI duration as in 8.2.
6. Open a PR titled "Add the e05 Spring Cloud Stream exercise", tick 6.7 and stop for Oskar's merge.
```

---

## 5. Later, not in this plan

- An observability exercise: correlation and causation IDs across a process, traces through the broker, and finding every message of one group checkout. It gets designed separately (spec Q7a).
