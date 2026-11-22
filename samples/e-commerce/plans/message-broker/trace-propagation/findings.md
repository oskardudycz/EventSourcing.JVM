# Trace propagation: findings

Research date: 2026-11-19. Read-only research. I read the source code and the documentation. I did not run any code, so the statements about runtime behaviour come from reading the source, not from a test run.

Sources pinned to these versions:

- KurrentDB Java client `io.kurrent:kurrentdb-client:1.2.1`: the jar in `~/.gradle/caches/modules-2/files-2.1/io.kurrent/kurrentdb-client/1.2.1/` and the GitHub tag `v1.2.1`, commit [`13957d06`](https://github.com/kurrent-io/KurrentDB-Client-Java/tree/13957d06ceace4f2bba5ade157f1d765cd96ab77). The jar has no sources jar, so I read the classes with `javap` and the source on GitHub. The constants in the jar match the source.
- KurrentDB .NET client: latest release `v1.6.1`, commit [`27d8cf64`](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/tree/27d8cf648635880d3f2c2d36128bb9fcf37065b7).
- Spring Boot `4.1.2` (the version in `samples/distributed-processes/build.gradle`). Its BOM manages OpenTelemetry `1.62.0` and Micrometer Tracing `1.7.2`.
- Emmett: `main`, commit [`0b9b5c7b`](https://github.com/event-driven-io/emmett/tree/0b9b5c7b3f16499b3019178b0aef15a082fa5e20) (2026-11-12).

## 1. What "alamanc" is

"alamanc" is a typo for **Almanac**. Almanac is a package in the Emmett repository: `src/packages/almanac`, published to npm as `@event-driven-io/almanac`.

- Emmett's `AGENTS.md` says: "`src/packages/almanac`: opinionated observability for event-driven systems." ([AGENTS.md#L12](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/AGENTS.md#L12))
- The package name is `@event-driven-io/almanac`, with the description "Almanac - Opinionated observability for event-driven systems" ([package.json#L2-L5](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/almanac/package.json#L2-L5)).
- PR [#332 "Add Almanac - observability package to streamline usability patterns"](https://github.com/event-driven-io/emmett/pull/332), merged 2026-04-23. It says Almanac exists so that Emmett does not always depend on OpenTelemetry and can also export to console, pino and analytics tools.
- PR [#369 "Threaded correlation and causation ids and aligned obervability context passing"](https://github.com/event-driven-io/emmett/pull/369), merged 2026-07-28, adds the correlation and causation flow that section 4 describes.

Almanac gives tracers (console, pino, OpenTelemetry, composite), a "scope" that carries the trace and message context, and testing helpers. Emmett's event stores, processors, workflows and command handlers use Almanac scopes to write and restore the context.

## 2. KurrentDB Java client 1.2.1

### 2.1 Metadata keys

The client writes two keys into the user metadata (`customMetadata`) of each appended event:

- `"$traceId"`: the trace id, 32 hex characters.
- `"$spanId"`: the span id, 16 hex characters.

Source: `ClientTelemetryConstants.Metadata.TRACE_ID = "$traceId"` and `SPAN_ID = "$spanId"` ([ClientTelemetryConstants.java#L6-L9](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetryConstants.java#L6-L9)). I confirmed the same values in the jar class `io/kurrent/dbclient/ClientTelemetryConstants$Metadata.class` with `javap -constants`.

The client does not write `traceparent`, `tracestate`, baggage, `$correlationId` or `$causationId`. The only keys are `$traceId` and `$spanId`.

### 2.2 Append

- `appendToStream` goes through `ClientTelemetry.traceAppend` ([AppendToStream.java#L33](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/AppendToStream.java#L33)). Multi-stream append and append-records go through `traceMultiStreamAppend` and `traceAppendRecords`.
- `traceAppend` starts a span named `streams.append`, kind `CLIENT` ([ClientTelemetry.java#L87-L120](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetry.java#L87-L120)). It does not set a parent explicitly (`parentContext` is `null`), so OpenTelemetry uses the current `Context` as the parent.
- The client writes the trace id and the span id of this `streams.append` span into the metadata. The stored `$spanId` is the append span, not the caller's span. The append span is a child of the caller's span, so the trace id is the caller's trace id.
- The client injects only when the span context is valid **and sampled** ([ClientTelemetry.java#L28-L42](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetry.java#L28-L42)). If the current trace is not sampled, the event gets no trace keys.
- The client rebuilds each `EventData` with `EventDataBuilder.binary(eventId, eventType, data, isJson)`. It keeps the event id, the type, the data and the content type. The test `testTracingContextInjectionDoesNotAffectEventBody` checks that the body and the content type do not change ([TracingContextInjectionTests.java#L15-L67](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/test/java/io/kurrent/dbclient/telemetry/TracingContextInjectionTests.java#L15-L67)).

### 2.3 Existing user metadata: merge, and overwrite of the two keys

Source: [ClientTelemetry.java#L44-L59](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetry.java#L44-L59).

- No metadata: the client creates a JSON object with only `$traceId` and `$spanId`.
- Metadata is a JSON object: the client parses it into a Jackson `ObjectNode`, keeps all other fields, and calls `put` for `$traceId` and `$spanId`. `put` **overwrites** these two keys if they already exist.
- Metadata is not a JSON object, or not JSON: the client catches the error and returns the metadata unchanged, without trace keys.
- Tests: `testTracingContextIsInjectedAsExpectedWhenUserMetadataIsJsonObject` and `testTracingContextInjectionIsIgnoredAsExpectedWhenUserMetadataIsNonNullAndNotAJsonObject` ([StreamsTracingInstrumentationTests.java#L40-L125](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/test/java/io/kurrent/dbclient/telemetry/StreamsTracingInstrumentationTests.java#L40-L125)).

Example. The sample writes `{"$correlationId":"order-1","$causationId":"msg-7"}`. With a sampled current span, the stored metadata becomes `{"$correlationId":"order-1","$causationId":"msg-7","$traceId":"4bf9…","$spanId":"00f0…"}`. If the sample writes its own `$traceId`, the client replaces it.

The sample already writes `$correlationId` and `$causationId` for commands (`core/events/EventMetadata.java`, `core/commands/CommandMetadata.java`, `EventSerializer.serialize(CommandEnvelope)`). Its mapper sets `FAIL_ON_UNKNOWN_PROPERTIES` to `false`, so the extra `$traceId` and `$spanId` keys do not break deserialization of these records.

### 2.4 Subscribe and read

- Catch-up subscriptions (`subscribeToStream`, `subscribeToAll`) call `ClientTelemetry.traceSubscribe` for each event ([AbstractRegularSubscription.java#L64-L79](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/AbstractRegularSubscription.java#L64-L79), [SubscriptionStreamConsumer.java#L28-L30](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/SubscriptionStreamConsumer.java#L28-L30)).
- Persistent subscriptions do the same ([AbstractSubscribePersistentSubscription.java#L96-L105](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/AbstractSubscribePersistentSubscription.java#L96-L105)).
- `traceSubscribe` reads `$traceId` and `$spanId` from the user metadata. It builds a remote parent with `SpanContext.createFromRemoteParent(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault())`. Then it starts a span named `streams.subscribe`, kind `CONSUMER`, with that parent, makes it current, and runs the listener's `onEvent` **inside** that scope ([ClientTelemetry.java#L61-L85](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetry.java#L61-L85), [#L206-L246](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetry.java#L206-L246)). So code that the listener runs on the same thread sees the restored trace as the current context.
- If the event has no valid trace keys, the listener runs without a span.
- The test `testCatchupSubscriptionIsInstrumentedWithTracingAndRestoresRemoteContextAsExpected` checks that the subscribe span's parent has the append span's trace id and span id and is remote ([StreamsTracingInstrumentationTests.java#L156-L208](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/test/java/io/kurrent/dbclient/telemetry/StreamsTracingInstrumentationTests.java#L156-L208)).
- **Reads are not traced.** `readStream` and `readAll` (`AbstractRead`, `ReadStreamConsumer`) have no tracing code. The `$traceId` and `$spanId` keys are in `RecordedEvent.getUserMetadata()`, but the client does not restore them. Code that reads a stream after a wait (for example, a webhook handler that loads the order stream) must read the keys and set the context itself.
- The documentation says the same: "The Java client automatically creates traces for append, catch-up and persistent subscription operations when OpenTelemetry is configured globally." ([docs.kurrent.io Java v1.2 Observability](https://docs.kurrent.io/clients/java/v1.2/observability.html), source [docs/api/observability.md#L160-L163](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/docs/api/observability.md#L160-L163)).

### 2.5 OpenTelemetry API and configuration

- The client depends on `io.opentelemetry:opentelemetry-api` (BOM 1.37.0), `opentelemetry-semconv` and `opentelemetry-semconv-incubating` 1.25.0-alpha, all with `runtime` scope in the published pom (`kurrentdb-client-1.2.1.pom`).
- It gets its tracer from **`GlobalOpenTelemetry.getTracer(...)`** on each operation ([ClientTelemetry.java#L22-L26](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetry.java#L22-L26)). There is no setting in `KurrentDBClientSettings` to pass an `OpenTelemetry` or a `Tracer`.
- The documentation tells you to build the SDK and call `buildAndRegisterGlobal()` ([docs/api/observability.md#L64-L105](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/docs/api/observability.md#L64-L105)). The client's own tests do the same ([TelemetryTests.java#L42-L63](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/test/java/io/kurrent/dbclient/TelemetryTests.java#L42-L63)).
- Span attributes: `db.system` = `kurrentdb`, `db.operation`, the stream, the subscription id, the event id, the event type, `server.address`, `server.port`, `db.user` ([ClientTelemetry.java#L248-L270](https://github.com/kurrent-io/KurrentDB-Client-Java/blob/13957d06ceace4f2bba5ade157f1d765cd96ab77/src/main/java/io/kurrent/dbclient/ClientTelemetry.java#L248-L270)).

### 2.6 Spring Boot 4.1.2 with Micrometer Tracing and the OpenTelemetry bridge

**Spring Boot does not register `GlobalOpenTelemetry`.**

- `OpenTelemetrySdkAutoConfiguration.openTelemetrySdk(...)` ends with `builder.build()`, not `buildAndRegisterGlobal()` ([OpenTelemetrySdkAutoConfiguration.java#L42-L54](https://github.com/spring-projects/spring-boot/blob/v4.1.2/module/spring-boot-opentelemetry/src/main/java/org/springframework/boot/opentelemetry/autoconfigure/OpenTelemetrySdkAutoConfiguration.java#L42-L54)). I also searched the jars `spring-boot-opentelemetry-4.1.2.jar` and `spring-boot-micrometer-tracing-opentelemetry-4.1.2.jar`: no class refers to `GlobalOpenTelemetry` or `buildAndRegisterGlobal`.
- The Spring Boot team closed PR [#46230 "Allow reusing registered global OpenTelemetry"](https://github.com/spring-projects/spring-boot/pull/46230) without merging it.

What happens then, from the source (not tested):

1. `GlobalOpenTelemetry.get()` with nothing registered sets a no-op `OpenTelemetry` as the global and returns it ([GlobalOpenTelemetry.java#L140-L157](https://github.com/open-telemetry/opentelemetry-java/blob/v1.62.0/api/all/src/main/java/io/opentelemetry/api/GlobalOpenTelemetry.java#L140-L157)). After that, `GlobalOpenTelemetry.set(...)` throws `IllegalStateException` "GlobalOpenTelemetry.set has already been called" ([#L171-L184](https://github.com/open-telemetry/opentelemetry-java/blob/v1.62.0/api/all/src/main/java/io/opentelemetry/api/GlobalOpenTelemetry.java#L171-L184)). So if the sample registers the Spring Boot SDK as global, it must do it before the first KurrentDB append or subscription.
2. The no-op span builder does not create a new span. `startSpan()` returns `Span.wrap(...)` of the explicit parent, or of `Span.current()` when there is no explicit parent ([DefaultTracer.java#L46-L62](https://github.com/open-telemetry/opentelemetry-java/blob/v1.62.0/api/all/src/main/java/io/opentelemetry/api/trace/DefaultTracer.java#L46-L62); OpenTelemetry 1.37.0 has the same code).
3. The Micrometer OpenTelemetry bridge keeps its current span in the OpenTelemetry `Context`. `OtelCurrentTraceContext.newScope` makes a new OpenTelemetry `Context` current that holds the span ([OtelCurrentTraceContext.java#L69-L95](https://github.com/micrometer-metrics/tracing/blob/v1.7.2/micrometer-tracing-bridges/micrometer-tracing-bridge-otel/src/main/java/io/micrometer/tracing/otel/bridge/OtelCurrentTraceContext.java#L69-L95)). Its `context()` falls back to `Span.current()` from the OpenTelemetry `Context` ([#L45-L58](https://github.com/micrometer-metrics/tracing/blob/v1.7.2/micrometer-tracing-bridges/micrometer-tracing-bridge-otel/src/main/java/io/micrometer/tracing/otel/bridge/OtelCurrentTraceContext.java#L45-L58)). `OtelTracer.currentSpan()` also falls back to `Span.current()` ([OtelTracer.java#L109-L123](https://github.com/micrometer-metrics/tracing/blob/v1.7.2/micrometer-tracing-bridges/micrometer-tracing-bridge-otel/src/main/java/io/micrometer/tracing/otel/bridge/OtelTracer.java#L109-L123)).

Result, case by case:

| Setup | Append writes | Subscribe restores | KurrentDB spans exported |
| --- | --- | --- | --- |
| Spring Boot default (no global) | `$traceId` and `$spanId` of the **caller's** current Micrometer span, if it is sampled | the remote context becomes current while `onEvent` runs; a Micrometer `nextSpan()` or an `Observation` in the listener becomes its child | no (`streams.append` and `streams.subscribe` are no-op) |
| Sample registers the Spring Boot `OpenTelemetry` bean as global, before the first KurrentDB call | `$traceId` and `$spanId` of the `streams.append` span (child of the caller's span) | `streams.subscribe` span (child of `streams.append`) is current while `onEvent` runs | yes, through the Spring Boot exporter |
| No Micrometer span is current at append time | nothing (span context is invalid) | nothing | no |

So the built-in propagation can work with Spring Boot's Micrometer and OpenTelemetry setup, but only for append and subscriptions, and only when a sampled span is current on the appending thread. I did not find any documentation or test from Kurrent or Spring that covers this combination. A test in the sample must prove it.

Sampling: Spring Boot's default `management.tracing.sampling.probability` is `0.10` ([TracingProperties.java#L65](https://github.com/spring-projects/spring-boot/blob/v4.1.2/module/spring-boot-micrometer-tracing/src/main/java/org/springframework/boot/micrometer/tracing/autoconfigure/TracingProperties.java#L65)). The client injects only sampled contexts (2.2), so with the default about 90% of events get no trace keys. The workshop sets `management.tracing.sampling.probability=1.0` in `workshops/event-driven-architecture/solved/src/test/resources/application.properties` (line 38).

Thread: the subscribe scope is current only on the thread that runs `onEvent`. The sample's `KurrentDBSubscription` calls the handler on that thread (`KurrentDBSubscription.subscribeToAll`), so the context reaches the handler. If a handler moves work to another thread, the context does not follow without extra code.

## 3. KurrentDB .NET client v1.6.1, for comparison

### 3.1 Keys

The same keys: `TracingConstants.Metadata.TraceId = "$traceId"`, `SpanId = "$spanId"` ([TracingConstants.cs#L5-L9](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Core/Common/Diagnostics/Core/Tracing/TracingConstants.cs#L5-L9)). The activity source name is `kurrentdb` ([KurrentDBClientDiagnostics.cs#L6](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Core/Common/Diagnostics/KurrentDBClientDiagnostics.cs#L6)).

### 3.2 Append

- Append runs inside `TraceClientOperation` (activity `streams.append`, kind `Client`) and injects `Activity.Current` into each event's metadata ([KurrentDBClient.Append.cs#L76](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Streams/KurrentDBClient.Append.cs#L76), [#L91](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Streams/KurrentDBClient.Append.cs#L91)).
- **Existing keys win.** If the metadata already has both `$traceId` and `$spanId`, the client keeps the metadata as it is. If it has only one, the client adds only the missing one ([EventMetadataExtensions.cs#L53-L104](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Core/Common/Diagnostics/EventMetadataExtensions.cs#L53-L104)). The new append API path (`StreamsClientMapper`) does the same with `if (!metadata.ContainsKey(...))` ([EventMetadataExtensions.cs#L10-L18](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Core/Common/Diagnostics/EventMetadataExtensions.cs#L10-L18), [StreamsClientMapper.cs#L37](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Streams/Streams/Model/StreamsClientMapper.cs#L37)). This is the opposite of the Java client, which overwrites.
- Non-object or invalid JSON metadata stays unchanged, as in Java.
- Injection happens only when the activity source has listeners, that is, when the app calls `AddKurrentDBClientInstrumentation()`.

### 3.3 Subscribe

- `TraceSubscriptionEvent` extracts `$traceId` and `$spanId`, starts a `streams.subscribe` activity (kind `Consumer`) with the remote parent, and **disposes it at once** ([ActivitySourceExtensions.cs#L30-L60](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Core/Common/Diagnostics/ActivitySourceExtensions.cs#L30-L60)). The event goes to the consumer later through a channel ([KurrentDBClient.Subscriptions.cs#L274-L285](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/src/KurrentDB.Client/Streams/KurrentDBClient.Subscriptions.cs#L274-L285)). So in .NET the handler does **not** run inside the restored context. The handler must read the keys and continue the trace itself. In Java the listener runs inside the scope (2.4).

### 3.4 Configuration and documentation

- `services.AddOpenTelemetry().WithTracing(b => b.AddKurrentDBClientInstrumentation())`, then an exporter ([docs/api/observability.md#L29-L56](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/docs/api/observability.md#L29-L56)). The published page: [docs.kurrent.io .NET v1.4 Observability](https://docs.kurrent.io/clients/dotnet/v1.4/observability). I did not find a published page for v1.5 or v1.6 (HTTP 404).
- Documentation text: "The .NET client currently creates traces for append, catch-up and persistent subscription operations." ([docs/api/observability.md#L95-L98](https://github.com/kurrent-io/KurrentDB-Client-Dotnet/blob/27d8cf648635880d3f2c2d36128bb9fcf37065b7/docs/api/observability.md#L95-L98)).
- Neither the Java nor the .NET documentation page names the `$traceId` and `$spanId` keys. Only the source code names them.

### 3.5 Server-side `$correlationId`

KurrentDB itself reads `$correlationId` from event metadata. The default property name is `"$correlationId"` ([CorrelationIdPropertyContext.cs#L7](https://github.com/kurrent-io/KurrentDB/blob/655e7b54dad4a524007d75d12c08ee456f32795b/src/KurrentDB.Core/Services/CorrelationIdPropertyContext.cs#L7)). The `$by_correlation_id` system projection links events to streams `$bc-<correlation id>`, and its `correlationIdProperty` parameter can change the property name ([System projections](https://docs.kurrent.io/server/latest/features/projections/system.html)). The pinned-by-correlation persistent subscription strategy also reads this property (`PinnedByCorrelationPersistentSubscriptionConsumerStrategy.cs`, found by GitHub code search). The server has no `$causationId` feature that I found.

## 4. Emmett and Almanac

### 4.1 Where the values live in event metadata

Emmett's recorded message metadata type has `messageId`, `correlationId`, `causationId`, `traceId` and `spanId` ([message.ts#L96-L105](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/typing/message.ts#L96-L105)). The keys have **no `$` prefix**. So Emmett's KurrentDB/EventStoreDB events do not match the client's `$traceId`/`$spanId` keys or the server's `$correlationId` property.

Every event store writes the same five keys on append:

- EventStoreDB/KurrentDB (`@eventstore/db-client` ^6.2.1): metadata `{ messageId, correlationId, causationId, traceId, spanId, ...userMetadata, kind, schemaVersion }` ([eventstoreDBEventStore.ts#L268-L305](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett-esdb/src/eventStore/eventstoreDBEventStore.ts#L268-L305)). The user metadata is spread **after** the generated values, so values that the caller sets win.
- In-memory ([inMemoryEventStore.ts#L232-L250](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/eventStore/inMemoryEventStore.ts#L232-L250)), PostgreSQL ([appendToStream.ts#L251-L264](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett-postgresql/src/eventStore/schema/appendToStream.ts#L251-L264)), MongoDB ([mongoDBEventStore.ts#L392-L438](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett-mongodb/src/eventStore/mongoDBEventStore.ts#L392-L438)).
- On read, the EventStoreDB store copies the stored metadata back into `message.metadata` ([eventstoreDBEventStore.ts#L395-L420](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett-esdb/src/eventStore/eventstoreDBEventStore.ts#L395-L420)).

The values come from the append scope: `instrumentAppend` opens the scope `eventStore.appendToStream` and the store reads `scope.context` ([eventStoreCollector.ts#L175-L192](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/eventStore/observability/eventStoreCollector.ts#L175-L192)). As in the KurrentDB Java client, the stored `spanId` is the append span's id, a child of the caller's span.

I found no message-broker header propagation in Emmett: no `traceparent` and no header code in `src/packages/emmett/src`. Emmett has no broker integration at this commit, so it gives no example for the broker-header part of the design.

### 4.2 How a scope resolves the four ids

Source: [scope.ts#L24-L47](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/almanac/src/scopes/scope.ts#L24-L47) and [#L112-L137](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/almanac/src/scopes/scope.ts#L112-L137).

- `traceId` and `spanId` come from the scope's span. With a no-op tracer, Almanac generates random OpenTelemetry-shaped ids (32 and 16 hex characters).
- `correlationId` comes from the inherited context. If none, the generator creates a new one.
- `causationId` comes from the inherited context. If none, it is equal to the `correlationId` (the code comment says: "an unseeded causation roots itself on the correlation").
- A child scope inherits the parent's context and can override `correlationId` and `causationId` through `options.context` ([scope.ts#L153-L183](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/almanac/src/scopes/scope.ts#L153-L183)).

### 4.3 From a handled message to the messages it produces

- **Processors (reactors, projections).** For each message, `startMessageScope` opens a scope `processor.message.<type>` with:
  - `parent` = `{ traceId, spanId }` from the handled message's metadata;
  - `context.correlationId` = the handled message's `correlationId`;
  - `context.causationId` = the handled message's **`messageId`**;
  - a link to the batch span.

  Source: [processorCollector.ts#L167-L208](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/processors/observability/processorCollector.ts#L167-L208). The code comment says: "whatever the handler appends is caused by the message it handles".
- The processor wraps the handler's `session.messageStore` in a proxy, so every `appendToStream` inside the handler uses the message scope without extra code. A handler that passes its own scope or parent wins ([processors.ts#L441-L475](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/processors/processors.ts#L441-L475), used at [#L644-L660](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/processors/processors.ts#L644-L660)).
- **Workflows.** `handleWorkflow` takes `correlationId` from the input message's metadata, and `causationId` = the input message's `messageId` (or a generated id). The caller can override both ([handleWorkflow.ts#L264-L287](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/workflows/handleWorkflow.ts#L264-L287)). The workflow processor appends outputs with the same rule: `correlationId` from the handled message, `causationId` = its `messageId` ([workflowProcessor.ts#L316-L352](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/workflows/workflowProcessor.ts#L316-L352)).
- **Command handlers.** They put the scope's `correlationId` and `causationId` on the span as attributes ([commandHandlerCollector.ts#L120-L131](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/commandHandling/observability/commandHandlerCollector.ts#L120-L131)). Without a seed from the caller, the root scope generates a new `correlationId`.

This matches the decided meaning: `correlationId` stays the same for the whole flow, and `causationId` is the id of the message that caused this one.

### 4.4 How a later handler restores the trace: `links` or `propagate`

Almanac has a setting `propagation: 'links' | 'propagate'` ([tracer.ts#L3](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/almanac/src/tracers/tracer.ts#L3)). The OpenTelemetry provider applies it ([otelTracer.ts#L60-L85](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/almanac/src/providers/otel/otelTracer.ts#L60-L85)):

- `'propagate'`: the stored `{traceId, spanId}` becomes the real parent (`trace.setSpanContext`). The handler's span joins the producer's trace.
- `'links'`: the handler starts a new trace from `ROOT_CONTEXT`, and the stored `{traceId, spanId}` becomes a span link.

**The default for processors and workflows is `'links'`** ([processorCollector.ts#L66](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/processors/observability/processorCollector.ts#L66), [workflowCollector.ts#L60](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/workflows/observability/workflowCollector.ts#L60)). The owner's decision (one trace for the whole business process) matches Emmett's `'propagate'` mode, not its default. The test "startMessageScope uses message trace context as parent when propagation is 'propagate'" covers that mode ([processorCollector.unit.spec.ts#L276](https://github.com/event-driven-io/emmett/blob/0b9b5c7b3f16499b3019178b0aef15a082fa5e20/src/packages/emmett/src/processors/observability/processorCollector.unit.spec.ts#L276)).

With either mode, `correlationId` and `causationId` continue in the message context. In `'links'` mode the business flow is still visible through `correlationId`, even when each step has its own trace.

## 5. Side by side

| Topic | KurrentDB Java 1.2.1 | KurrentDB .NET 1.6.1 | Emmett (Almanac) |
| --- | --- | --- | --- |
| Trace keys in event metadata | `$traceId`, `$spanId` | `$traceId`, `$spanId` | `traceId`, `spanId` |
| Correlation and causation keys | none | none | `correlationId`, `causationId`, plus `messageId` |
| Stored span id | the `streams.append` span (child of caller) | `Activity.Current` inside the append activity | the `eventStore.appendToStream` scope span (child of caller) |
| Existing trace keys in user metadata | overwritten | kept | caller's values win (spread last) |
| Other user metadata | kept (JSON object only) | kept (JSON object only) | kept |
| Restore on subscription | yes, listener runs inside `streams.subscribe` | span is started and closed at once; handler not inside it | yes, per-message scope with the stored parent |
| Restore on read | no | not checked | the metadata is returned; a handler can pass it as `parent` |
| Default parent or link | parent | parent | link (`'links'`); parent with `'propagate'` |
| Configuration | `GlobalOpenTelemetry` only | `AddKurrentDBClientInstrumentation()` | `observability: { tracer, propagation, ... }` |
| Broker headers | not applicable | not applicable | none found |

## 6. Facts that the Java sample's specification must handle

These are facts from the sources above, mapped to the decided design. They are not a new design.

1. If the sample stores its own trace context under `$traceId` and `$spanId`, the Java client overwrites them on every sampled append. If the sample stores it under other keys, the client still adds `$traceId` and `$spanId` when a sampled span is current. Both can live in the same JSON object.
2. The built-in restore covers only catch-up and persistent subscriptions. Handlers that react after a wait and load state by reading a stream must restore the context from the stored metadata themselves.
3. The built-in client spans (`streams.append`, `streams.subscribe`) reach the exporter only if the sample registers the Spring Boot `OpenTelemetry` bean as `GlobalOpenTelemetry` before the first KurrentDB call. Without that, the keys still follow the current Micrometer span, but the client spans are not recorded.
4. The client injects only sampled contexts. With Spring Boot's default sampling probability `0.10`, most events get no trace keys.
5. The sample already writes `$correlationId` and `$causationId` for commands. KurrentDB's `$by_correlation_id` projection reads `$correlationId` by default. Emmett uses the same meaning, under the keys `correlationId` and `causationId` without `$`.
6. The workshop's Kafka setup carries `correlationId` and `causationId` as Micrometer baggage (`management.tracing.baggage.remote-fields=correlationId,causationId`), and `KafkaMessageBaggageHandler` sets `causationId` from the `MESSAGE_ID` header on receive (`KafkaMessageBaggageHandler.java` lines 29-35). Emmett has no broker example to compare with.

## 7. Not found or not verified

- I did not run any code. Section 2.6 comes from reading the source of the client, the OpenTelemetry API, the Micrometer bridge and Spring Boot.
- I found no documentation page that names the `$traceId` and `$spanId` keys. Only the client source names them.
- I found no published .NET observability page for v1.5 or v1.6. The v1.4 page and the in-repository `docs/api/observability.md` at v1.6.1 have the same content.
- I did not check the .NET client's read path for tracing.
- I did not check the KurrentDB Node.js package `@kurrent/opentelemetry`. Emmett's EventStoreDB store does not use it; it writes its own keys.
- I found no broker header propagation in Emmett.
