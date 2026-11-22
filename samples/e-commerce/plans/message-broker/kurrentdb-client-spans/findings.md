# KurrentDB client spans: findings

Research for open item P3 (plan r7). Question: can the KurrentDB Java client's `streams.append` span be a child of the caller's span, so the purchase stays one trace and the client spans reach Tempo? Written by the coordinator from the research subagent's report (the harness stopped the subagent writing files).

## Sources

- Java client `v1.2.1` (commit `13957d06`), the latest release on GitHub (2026-06-25) and Maven Central.
- Java client `main` (commit `ca0c5e56`), 4 commits not in a release.
- opentelemetry-java-instrumentation `main` (commit `0bd749fd`).
- JDK 22 `CompletableFuture.java`.

## 1. How client 1.2.1 builds the append span

- `AppendToStream.java#L32-L40`: `execute()` calls `client.run(channel -> ClientTelemetry.traceAppend(...))`.
- `ClientTelemetry.java#L92-L102`: `traceAppend` calls `createSpan(APPEND, CLIENT, null, ...)`, parent `null`; `#L252-L253` sets a parent only when not null; `#L269` `startSpan()` takes the parent from `Context.current()` of the running thread.
- `ClientTelemetry.java#L22-L26`: the tracer comes only from `GlobalOpenTelemetry`; `#L28-L30`: without a registered global the span is invalid and the client writes no keys.
- Multi-stream append (`#L129-L137`) and `appendRecords` (`#L175-L183`) also pass `null`.

The thread hop: the caller's thread queues a `RunWorkItem` (`GrpcClient.java#L33-L68`); the connection loop thread (`esdb-client-<uuid>`, a private single-thread executor, `KurrentDBClientBase.java#L32-L40`, `ConnectionService.java#L44-L46`, `#L155-L181`) completes it; `runWithArgs` continues with `thenComposeAsync(...)` without an executor (`GrpcClient.java#L78-L80`), so `traceAppend` runs on `ForkJoinPool.commonPool()`, triggered from the loop thread (JDK `CompletableFuture` lines 566-575, 1172-1173). No step carries an OpenTelemetry `Context`, so the span starts a new trace. This matches the measurement in step 4.

What the client lets us configure: no executor, `OpenTelemetry` or `Tracer` setting; `AppendToStreamOptions` has only `authenticated`, `requiresLeader`, `deadline`, `header(k, v)` (`OptionsBase.java#L36-L97`); `ConnectionSettingsBuilder.addInterceptor` exists (`#L211-L217`), but the interceptor runs on the `commonPool` thread after the span exists. The client's tests check only that the subscribe span is a child of the append span (`StreamsTracingInstrumentationTests.java#L194-L199`).

Correction: `trace-propagation/findings.md` section 2.2 and its section 5 "Stored span id" row say the append span is a child of the caller's span. That is wrong for 1.2.1.

## 2. Newer code, issues and PRs

No release after 1.2.1. `main` has PR #401 (issue #400): it doesn't change the parent, but writes `$traceparent`/`$tracestate` for every valid span, writes `$traceId`/`$spanId` only for sampled spans and removes them for valid unsampled ones, and still writes nothing without a global. No issue or PR reports the missing parent (searched: #400, #326, #273, #280).

## 3. OpenTelemetry Java options against this client

| Option | Works? | Why |
|---|---|---|
| `Context.current().wrap(...)` around our call | No | The hop happens inside the client; it takes no task or executor from us. |
| `Context.taskWrapping(executor)` | No | The client accepts no executor. |
| Our own `ClientInterceptor` | No | Runs after `createSpan`; a started span can't get a new parent. |
| OTel gRPC library interceptor | No | Uses `Context.current()` on the `commonPool` thread: the orphan span or root. |
| OTel Java agent | No (from source, not run) | Captures the context of the submitting thread, which is the loop thread with the root context; works only by timing; a second instrumentation next to Micrometer. |
| `traceparent` header plus a custom interceptor | Rejected | Doesn't fix the client's span, sends the header to the server, more code than 5.1. |
| Fork, reflection, bytecode, custom `ContextStorageProvider` or `ForkJoinPool` thread factory | Rejected | Not allowed (no monkey-patching). |

The .NET client works because .NET carries `Activity.Current` across `await` (`KurrentDBClient.Append.cs#L76`); the JVM has no equivalent across `CompletableFuture` pools.

## 4. Recommendation

**A. Our own append span, client telemetry off (recommended).** Don't register `GlobalOpenTelemetry`. Wrap each append in a Micrometer `Observation` on the caller's thread:

```java
Observation.createNotStarted("streams.append", observations)
        .lowCardinalityKeyValue("db.system", "kurrentdb")
        .lowCardinalityKeyValue("db.operation", "streams.append")
        .highCardinalityKeyValue("db.kurrentdb.stream", streamId)
        .observe(() -> /* serialize(events) + appendToStream(...).get() */);
```

- The span is a child of the caller's span and reaches Tempo; the purchase stays one trace. The client writes no keys, on 1.2.1 and on `main`.
- Stored keys: `$traceId` is the caller's trace. `$spanId` is our append span if `serialize` runs inside `observe`, or the caller's span if it runs before (today's behaviour).
- Lost: the client's own `streams.append` and `streams.subscribe` spans with `server.address`, `server.port`, `db.user`. Consumers still continue the trace from the stored keys.
- Code: `KurrentDBEventStore.append` (both overloads) takes an `ObservationRegistry`; `CommandHandler.java:45` and `EntityStore.java:106` call `appendToStream` directly and need the same wrapper or one shared helper.

**B. Global plus a sampler that drops the orphan span (rejected).** Keeps the store's keys on 1.2.1, but `main`'s PR #401 removes `$traceId`/`$spanId` for unsampled spans and writes a new-trace `$traceparent`, so it breaks on the next release, and it gives no append span without A.

**C. Upstream fix.** The client should capture `Context.current()` in `AppendToStream.execute()` and use it as the parent, or run the `thenComposeAsync` step on `Context.current().wrap(executor)`. No issue exists yet. Once released, registering the global would make the client write its own keys over the store's, with `$spanId` set to its append span.

## Not verified

The OTel Java agent was not run; no experiment beyond step 4's measurement; the Node client was not read.
