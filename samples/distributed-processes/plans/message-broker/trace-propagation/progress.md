# Trace propagation research: progress

## Checked

- Created the research folder.
- KurrentDB Java client 1.2.1: jar classes (`ClientTelemetry`, `ClientTelemetryConstants$Metadata`, `SubscriptionTracingCallback`), pom dependencies, GitHub source at tag `v1.2.1` (commit `13957d06`), tests, `docs/api/observability.md`.
- Spring Boot 4.1.2 jars `spring-boot-opentelemetry` and `spring-boot-micrometer-tracing-opentelemetry`: no call to `GlobalOpenTelemetry` or `buildAndRegisterGlobal`. Spring Boot PR 46230 (closed). Default sampling probability 0.10.
- OpenTelemetry Java API 1.62.0: `DefaultTracer` (no-op span builder) and `GlobalOpenTelemetry`.
- Micrometer Tracing 1.7.2 OTel bridge: `OtelCurrentTraceContext`, `OtelTracer`.
- KurrentDB .NET client v1.6.1 (commit `27d8cf64`): `TracingConstants`, `EventMetadataExtensions`, `ActivitySourceExtensions`, append and subscription call sites, `docs/api/observability.md`.
- KurrentDB docs pages: Java v1.2 observability, .NET v1.4 observability, server system projections (`$by_correlation_id`). Server source `CorrelationIdPropertyContext` (`$correlationId`).
- Sample code: `KurrentDBEventStore`, `KurrentDBSubscription`, `EventSerializer`, `core.events.EventMetadata`, `core.commands.CommandMetadata`.
- Emmett (commit `0b9b5c7b`): "alamanc" is Almanac (`src/packages/almanac`, `@event-driven-io/almanac`). Read Almanac scope and OTel tracer, Emmett message metadata type, event store collector, EventStoreDB/in-memory/PostgreSQL/MongoDB append, processor collector and processors, workflow handler and processor, PRs 332 and 369.
- Workshop Kafka baggage handler and settings (context only).

## Left

- `findings.md`: the tool harness refused to let the research subagent write this file ("Subagents should return findings as text"). The full findings text went back to the caller in the subagent report. The caller must save it here as `findings.md`.
