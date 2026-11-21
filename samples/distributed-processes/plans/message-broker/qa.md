# Q&A: message broker and observability for distributed-processes

Idea: move the Kafka or RabbitMQ setup (switchable through config) from `workshops/event-driven-architecture/solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses`, so the sample gets a proper message bus, observability also in tests, a Grafana setup and propagation. Double-check that it is idiomatic and safe enough.

## Q1

Where should the broker setup go: into `samples/distributed-processes`, replacing its in-memory bus as step 8, or somewhere else, such as a new sample or the workshop's own `src/main`?

**Answer:** yes, but it should be additional implementation to in memory, so it's switchable

## Q2

Should the workshop keep its Kafka and RabbitMQ solutions as they are, so the sample gets a copy, or should we remove them from the workshop and link the e03 README to the sample?

**Answer:** Workshop should stay as it is, this is a work based on what we did there

## Q3

Should this work cover only the message bus and observability, with the in-memory event store and projections unchanged? Or should it cover all of step 8, including the event store and the JPA projections?

**Answer:**
1. it'd be good if we could switch between RabbitMQ and Kafka to showcase those two, but if it's too hard then Kafka.
2. Start with KurrenDB, but copy also PostgreSQLEventStore here, and similar story as in 3. Yes
4. if that make sense, but mostly we can use JPA for dbs with in memory also, right?

## Q4

Can JPA work with an in-memory database? Options: A. No in-memory database for JPA: the projections keep the `HashMap` implementation and get a JPA one on PostgreSQL. B. JPA everywhere, with H2 for the in-memory setup. Recommended: A.

**Answer:** B, but for integration testing we should use real PostgreSQL and also allow to swtich to in-memory by config

## Q5

Do you agree with the two layers (contract tests per adapter against its real server, end-to-end tests on a few chosen combinations)? If you do, which combinations should the end-to-end tests run, in the build and in CI?

**Answer:** That gives 3 × 3 × 2 = 18 combinations. If ECommerceTests and the API tests ran every combination, the build would start brokers and databases dozens of times, and CI time would grow by an estimated 10 to 15 minutes.

Are you nuts? We should have docker compose by default and test containers when needed. Also why would we need so many 2e2 tests with brokers?

Follow-up to Q5 (after the reply: business end-to-end tests run once in memory, broker behaviour in per-bus tests, at most one end-to-end wiring test per broker, Docker Compose first and Testcontainers when needed):

**Answer:** Definitely we should add contract tests using strictland as event versioning, but that's orthogonal.

Question from Oskar (about "The same split works for the event stores and the projections. Their contract tests run against KurrentDB, PostgreSQL and H2."):

> heir contract tests run against KurrentDB, PostgreSQL and H2 - what do you mean?

Reply: the same store tests run for in-memory, KurrentDB and PostgreSQL; the projection tests run on PostgreSQL by default and on H2 by config; business tests such as `ECommerceTests` don't repeat for each of them.

**Answer:** ok

Open: which tool "strictland" means for the contract tests with event versioning. Oskar called it orthogonal, so it goes to the spec as a separate follow-up.

## Q6

Should the broker carry every channel (A: every channel, `<module>-internal` included; config swaps the whole bus), or only the channels between modules (B: `<module>-internal` stays in memory)? Recommended: A.

**Answer:** if it goes to broker then it should cary this channel, so probably 1

## Q7

Do you agree that the trace context, `correlationId` and `causationId` go into the event metadata, with one trace per action up to the next wait, and `correlationId` linking the traces of one purchase?

Question from Oskar:

> with one trace per action up to the next wait, and correlationId linking the traces of one purchase? - what do you mean?

Reply (short): a trace ends where the purchase waits for something outside the shop; the next step starts a new trace; one purchase gives about five traces, tied together by the same `correlationId`.

> Stop mansplain me and answer my question

**Answer:** Dude, trace should represent the whole process with propagation, and spans per step. For correlation it should be business trace and causation should be previous message id, so like a business parent step id

## Q8

What should the tests check about observability? Suggestion: catch spans in memory (`InMemorySpanExporter`, as the workshop does), check that one purchase is one trace with the right `correlationId` and `causationId` on each step; when Grafana LGTM runs from Docker Compose, the same tests also send their traces there. Or more, such as checks on metrics or logs?

Question from Oskar:

> Didn't we have that already in exercises/

Reply: the checks are in `solved` (`MessageMetadataPropagationTest`, `KafkaErrorHandlingTest`, `RabbitMQErrorHandlingTest`, Grafana LGTM in the workshop's `docker-compose.yml`). Should the sample's observability tests be the workshop's checks plus the event store hop and the waits, and nothing more?

**Answer:** Dude, that's what I mean by solved. Man, can we not round in circles?

Decision: the workshop's solved checks, plus the trace across the event store and across the waits.

## Q9

Can I start two research subagents: `broker-setup-review/` (is the workshop's Kafka and RabbitMQ setup idiomatic and safe) and `trace-propagation/` (how real projects carry the trace context through an event store and across long waits)?

**Answer:** why do you need the trace propagation research? I told you how to do it.  You can check ofc kurrentdb as it has built in, and check how emmett propagates it through alamanc.

And yes do it

Decision: the trace design stays as Oskar answered in Q7. The second research only checks how KurrentDB's built-in propagation and Emmett implement it.

Research done: `broker-setup-review/findings.md` and `trace-propagation/findings.md`.

## Q10

Which key names should the event metadata use? Recommended: KurrentDB's names for all three stores: `$correlationId`, `$causationId`, `$traceId`, `$spanId`, with the event ID as `messageId`. Other option: Emmett's names without `$`.

**Answer:** fine by me, but this is just what to map to kurrent, we can have a regular name without $ and map it right?

Reply: yes; `EventMetadata` uses plain names (`messageId`, `correlationId`, `causationId`, `traceId`, `spanId`); in-memory and PostgreSQL store them as they are; `KurrentDBEventStore` maps them to `$correlationId`, `$causationId`, `$traceId`, `$spanId` and back; `messageId` is the KurrentDB event ID.

**Answer:** ok, then move on

## Q11

Should `bootRun` start real infrastructure through Docker Compose by default (B: `spring-boot-docker-compose` starts Kafka, KurrentDB, PostgreSQL and Grafana LGTM; one property switches back to all in memory), or stay in memory by default (A)? If B, which broker is the default: Kafka or RabbitMQ?

**Answer:** B kafka is the default

## Q12

Should the sample get the workshop's failure handling (`MessageFailureStrategy`, `RetryBackOff`, retries with back-off, dead-letter topic or queue, `messaging.dead_lettered` counter) and message versioning (`VersionedMessageConverter`, `MessageMappingRegistry`, `MessageTransformations`), both with the review's fixes (one versioning copy N8, stable type names and trusted package U2, dead-letter per queue N5, quorum queues U4)?

**Answer:** yes

## Q13

Grafana setup: A. as in the workshop (traces and metrics, Explore); B. A plus logs to Loki over OTLP with the trace ID on each line; C. B plus a provisioned dashboard for the purchase flow. Recommended: B.

**Answer:** Dude, simple, all capabilities should be there

Decision: C (traces, metrics, logs, and a provisioned dashboard).

## Q14

Are you ready for me to write the specification? Defaults for the points not discussed: spec at `plans/message-broker/spec.md`, step 8 of `plans/saga-and-outbox/plan.md` links to it and Q5 and Q7 there are marked answered; Springwolf documents the servers of both brokers; README and HOWTO describe the switches, the Docker Compose default and Grafana; CI keeps `./gradlew build` and `./gradlew eventCatalogTest`, bus and store tests use Testcontainers there; the workshop stays unchanged; contract tests with event versioning ("strictland") are a follow-up outside this work.

**Answer:** yes

## Q15 (spec r1)

Do these six test seams match what you expect? 1. `MessageBus`: one shared test set for in-memory, Kafka and RabbitMQ, from `InMemoryMessageBusTests`, plus the workshop's retry and dead-letter tests for the brokers. 2. `EventStore`: one shared test set for in-memory, KurrentDB and PostgreSQL, from `InMemoryEventStoreTests`, plus the metadata round trip. 3. Projections: `ProjectionTests` on JPA with PostgreSQL and H2. 4. The application through `ApiSpecification`: API tests stay in memory; one purchase on Kafka and one on RabbitMQ, both with KurrentDB and PostgreSQL, check the order completes and `InMemorySpanExporter` shows one trace with the right `correlationId` and `causationId`. 5. Grafana: one test against Grafana LGTM in Testcontainers (`LgtmStackContainer`): trace in Tempo, logs in Loki, metrics in Prometheus, dashboard loaded. 6. `ECommerceTests`: unchanged, in memory.

Gaps found and stored as open items in spec r1: JPA shape for `Database.find(Class, Predicate)`; PostgreSQL subscriptions (the workshop store has none; polling by `global_position` can skip events); stored checkpoints (`KurrentDBEventStore` starts from the beginning on every start); where checkpoints live.

**Answer:** Sounds good

## Q16 (spec r1)

O7: approve the review fixes not yet approved? U1 `earliest`; U3 declared topics, no auto-create; N1 producer timeouts inside the publish timeout; N2 dead-letter `KafkaTemplate` as a bean; N3 `messageId` from the stored event ID; N4 Kafka record key from the route key or the entity ID, not from the first record field by reflection; N6 `mandatory` only on `requests` and `internal`; N7 separate RabbitMQ publisher connection; C5 an unknown switch value fails the start.

**Answer:** Till N4 - ok

N4 - The Kafka record key comes from the first record field, found by reflection.  - wot?! We have routing config afaik in workshop

N6,N7, C5 - ok

Decision: U1, U3, N1, N2, N3, N6, N7 and C5 approved. N4 open: see Q17.

## Q17 (spec r1)

N4: Kafka record key for routes without a key. A: `null`, Kafka spreads the messages over partitions. B: `routeKey` with the `EntityId` value for every command and internal event (`referenceId` for `ReserveStock`), as Debezium (`aggregateid`), Axon (`SequentialPerAggregatePolicy`) and Ecotone (aggregate identifier) do.

**Answer:** A makes sense, but double check best practices

Then: We don't have tnity id, right?

Then: Personally I'm not a fan of that, to me it's an anti pattern to tell on event what's the routing key, it ties message with the channel, which is wrong

Follow-up: B does not change the messages; the route config reads the ID. Option C: `correlationId` from the metadata as the key.

**Answer:** I mean in general entity id make sense, but not as some sudo interface or adnotation

Decision (spec r2): B with `routeKey` in the route config.

**Answer:** C makes sense, but we should ensure we don't have such scenario

Clarification asked: is this C from Q17, and which scenario?

**Answer:** Is this C from Q17, so that the key is the correlationId and the routeKey lines are not needed? - yes

Decision (spec r5): C. The Kafka record key is the `correlationId` from the metadata; no `routeKey` lines. The scenario to prevent: see Q19.

## Q18 (spec r2)

O1: JPA shape for `Database.find(Class, Predicate)`. A: one shared document table, filter in memory. B: one JPA entity and table per view, queries in SQL.

**Answer:** ): Database.find - dude we should just JPA ideiomatically

Decision (spec r5): each view is a JPA `@Entity` with a Spring Data `JpaRepository`; queries are repository methods; `Projection` uses the repository; `core/projections/Database` goes.

## Q19 (spec r5)

Which scenario must Q17's key rule out? A: a message published with no `correlationId`. B: two messages of one purchase handled at the same time by two consumers. C: something else.

**Answer:** Dude, just ensure you registered all routings

Decision (spec r6): a test checks that every message type the four modules publish through the bus gets a channel from the modules' routes (criterion 15.2). `InMemoryMessageBus.publish` skips a message without a route silently, so the test is the guard.

## Q20 (spec r6)

What should `publish` do with a message that has no route, on all three buses? A: skip it, as `InMemoryMessageBus` does now. B: fail the publish with an error that names the message type.

**Answer:** B

Decision (spec r7): criterion 15.3; all three buses fail the publish; `InMemoryMessageBus.publish` stops skipping.

## Q21 (spec r7)

O2: how should the PostgreSQL store deliver events to subscribers? A: polling with a transaction ID guard, as Emmett does (`pg_snapshot_xmin`). B: polling with a table lock on append.

**Answer:** A

Decision (spec r8): A; criterion 27.2.

## Q22 (spec r8)

O3 and O4: should subscriptions store a checkpoint, and where? A: no checkpoint, start from the beginning. B: in the same store as the events (KurrentDB checkpoint stream, PostgreSQL table). C: all checkpoints in the projections' PostgreSQL, whichever event store runs.

**Answer:** C

Decision (spec r9): C, through a JPA entity and repository; `subscribe` gets a subscription name; criteria 26.2 and 27.3. The in-memory store keeps no checkpoint, because its events are gone after a restart.

**Follow-up answer:** i mean like emmett

Decision (spec r10): as in Emmett (https://event-driven.io/en/checkpointing_message_processing/): checkpoints live where the side effects live, in the projections database; `processor_checkpoints` with `processor_id`, `last_processed_position`, `last_processed_transaction_id`, through JPA; a projection stores its change and checkpoint in one transaction; the update names the position it expects to replace (same: success; at or past: ignored and rolled back; older: another instance, fails); criteria 27.4 and 27.5.

**Follow-up answer:** not a total copy but simple enough

Decision (spec r11): keep the checkpoint table, one transaction for a projection's change and its checkpoint, and skip when the stored checkpoint is at or past the new position. Emmett's other result codes and the competing-instance detection are not copied. The PostgreSQL poller orders by transaction ID, then `global_position`, and continues after both, as Emmett does; ordering by `global_position` alone could skip an event whose transaction committed late.

## Q23 (spec r11)

O5: which value is the `correlationId` of a purchase? A: the order's URN. B: the cart's URN. C: the `messageId` of the first command, as in the workshop.

**Answer:** A for now

Decision (spec r13): A, the order's URN; marked in the spec as the choice for now.

## Q24 (spec r13)

O6: how do "Pay now" (`CreateCheckoutSession`) and "Retry payment" (`RetryOrderPayment`) join the purchase's trace? A: the step continues the purchase's trace from the order's events, the HTTP span stays in its own trace, and the step span has a span link to it. B: the HTTP span joins the purchase's trace through a `traceparent` header from the browser.

**Answer:** A

Decision (spec r14): A; criteria 33.2 and 33.3. No open items left.

## Q25 (spec r14, review 20261120T073425)

Review triage. Three findings need Oskar: 1. `ECommerceTests` runs on H2 through JPA (Q&A-1, Quality-16). 2. `correlationId` and Kafka key of messages published outside a purchase (Q&A-4, Quality-6). 3. Drop the order-outcome counter; dashboard from existing metrics only (Q&A-9, Quality-21). Fourteen others get the answer that follows from earlier answers or the code (table in the conversation: Q&A-5/Quality-11, Q&A-10, Quality-2, 3, 4, 7, 8, 9, 10, 12, 13, 15, 20, 22).

**Answer:** ok, but we also need e2e tests
1. ok
2. I'm not sure whow would that work
3. What are metrics that exist?

Follow-up: e2e tests on real infrastructure stay (36.1 Kafka purchase, 37.1 RabbitMQ purchase, 41.1 Grafana; `ShopInBrowserTests`). For 2: publishers outside a purchase set nothing, the handler restores the context (Q7), the message has a `null` Kafka key. For 3: existing metrics are the Spring Kafka/AMQP observation timers, `messaging.dead_lettered`, `http.server.requests`, JVM and process metrics; no retry metric, no business metric.

**Answer:** 2. In general what you (and KurrentDB) are calling correlation id is actually conversation id, correlation id, should be specific handler scope so e.g. coomand handling, event handling etc.

nly that one message per wait goes to any partition.  - what does it even mean?

I told you also that we should register all routings and have explicit routing and intentional policy.
3. ok

Decision: 1 and 3 accepted (dashboard from existing metrics only, no retries panel, no order outcomes). The business process ID (the order's URN, Q23) is the `conversationId`, not the `correlationId`; `correlationId` is scoped to one handling. Open: the exact `correlationId` value, the KurrentDB mapping, and the Kafka key under explicit routing (Q26).

## Q26 (spec r14)

The `correlationId` of the first message (`CheckOutShoppingCart`): what marks a handling scope? A: the `messageId` of the handled message. B: a new ID per handling. Also: does every message type get its Kafka key from its own route, and does a type with no key in its route fail?

**Answer:** 1 ? - it should be automatically generated, dude, just check what emmett does and conform

Checked on Emmett `main` (`src/packages/almanac/src/scopes/scope.ts`): "`correlationId` groups a whole flow, `causationId` names the direct cause"; `correlationId: inherited.correlationId ?? generator.generateCorrelationId()`; `causationId: inherited.causationId ?? correlationId` ("an unseeded causation roots itself on the correlation"). Processors set `correlationId` from the handled message and `causationId` = its `messageId`. Emmett's metadata has no `conversationId`; `attributes.ts` only defines the OpenTelemetry name `messaging.message.conversation_id`, and nothing uses it.

Decision: open until Oskar confirms how to conform (Q27).

## Q27 (spec r14)

Conform to Emmett: `correlationId` generated by a step with nothing to inherit and inherited by the whole flow, `causationId` = the handled message's `messageId`, no `conversationId`; the order's URN is no longer the `correlationId` (Q23 changes); the Kafka key comes from each message type's explicit route (Q17 changes). Or add `conversationId` as a sixth value?

**Answer:** yes, if you can use order id or other predictable id, that's fine , byt if it's a challeng ehten not

Then: Can we move on?

Decision (spec r15): conform to Emmett, no `conversationId`. The `correlationId` is generated, because seeding it with the order's URN at checkout would make the cart module know `OrderId`. The Kafka key is the route's routing key, the predictable entity ID each message carries (`Urns.routingKeyOf`), set explicitly in every route. Q25's fourteen defaults, 1 (`ECommerceTests` on H2) and 3 (dashboard from existing metrics) are in r15.

## Q28 (spec r15)

Review findings Q&A-3, Q&A-6, Q&A-7: criteria 1 to 4 have no test seam. Proposal: a Docker Compose start test for 1.1, 1.2 and 3.1; for 4.1, keep the RabbitMQ purchase on the PostgreSQL store or add a third purchase test.

**Answer:** Docker compose start test? Can't you keep what you have and port what's missing from the solutions?!

Decision (spec r16): no new tests. The RabbitMQ purchase test keeps the PostgreSQL event store (r14 setup, covers 3.1 and 4.1); 2.1 is covered by the in-memory API tests; the Docker Compose start has no automated test. The missing workshop solved checks are ported: the metadata, baggage, `RabbitMQPublishTest`, `MessageFailureStrategyTest` and `RetryBackOffTest`.

## Q29 (spec r16)

Apply the seven remaining review fixes (Q&A-2, Q&A-8, Quality-1, 14, 17, 18, 19) as proposed?

**Answer:** ok, and write plan

Decision (spec r17): applied; review approved; next the plan.

## Q30 (plan r3)

Oskar, on the plan review: "if they finish and issues are solvable on your own, then solve them and run plan with sonnet, if some are left, then put them in the end and I'll answer them later (my guess is that we shouldn't have show stoppers)"

Decision (spec r18, plan r3): findings settled from the spec, the Q&A and the code are fixed. Spec r18: criterion 28.1 follows the spec's own KurrentDB decision (the client writes its append span's `spanId`, trace-propagation findings 2.3); the view tests run on both databases, following criterion 29. Two findings wait for Oskar at the end of the plan.

## Q31 (spec r19, plan r4)

Step 4 returned `planNeedsChange`: the KurrentDB Java client 1.2.1 builds its append span on its own worker thread, so the caller's context is lost (measured). Spec r18's 28.1 ("on KurrentDB a `spanId` of the client's append span, whose parent is the current span") can't hold.

Decision (coordinator, under Q30): spec r19 restores 28.1 to its r17 text ("the same ... `traceId` and `spanId`, on each store"), which holds without the global OpenTelemetry. The global registration and the KurrentDB client spans in Tempo become open item P3 for Oskar; the steps run without the global until then.

## Q32 (plan r7)

Four open questions: P1 where "Pay now" and "Retry payment" open their step span; P3 the KurrentDB client's spans; P2 the Compose Kafka in a dev container; the flaky catalog check `catalog.spec.js:153`.

**Answer:** P1: I don't understand do you want to manually setup spans in facades? P3: Research a fix first. P2: Document the limit (Recommended). Flaky test: Fix it in a small step (Recommended).

Decision (plan r7): P2 answered, documented in step 16. Flaky check: new step 11a. P3: research by a fresh subagent in `kurrentdb-client-spans/`. P1: open, Oskar asked a question back.

## Q33 (spec r21, plan r8)

P1: should "Pay now" and "Retry payment" open their step span through `MessageContext.handling` in the facades, or skip the span and split the trace? Follow-ups: their new `correlationId` breaks 36.1; the store could continue from the stream's last event when nothing is inherited.

**Answer:** I'\d prefer have a regular code for typical scenarios, so no manual plumbin unless it bring sthe value, adn I don't see this value here

Then: Dude, seriously, I told you what do I mean by correlation and how to proceeedd, as long as business wise we're able to make the flow working we can sort it out later, right? If not, then you need to be more precise and explain me bettwr

Decision (spec r22): no manual span; "Pay now" and "Retry payment" are plain facade calls that start a new flow (new `correlationId` and trace, per Q27). The business flow doesn't depend on it. 33.2 and 33.3 are removed; 36.1 checks two flows split at "Pay now"; linking them is a follow-up (Out of Scope).
38.1 also names the first flow's `correlationId` (spec r22).

## Q34 (spec r23, plan r10)

P3: should `KurrentDBEventStore.append` create its own `streams.append` span (a Micrometer Observation around `appendToStream`, one class, no handler changes), or should 38.1 drop the KurrentDB spans? Research: `kurrentdb-client-spans/findings.md`.

**Answer:** Add streams.append span

Decision (spec r23, plan r10): step 14 adds the Observation; the global OpenTelemetry stays unregistered; 38.1 checks the `streams.append` spans inside the steps.
