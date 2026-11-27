# Tools and versions

Checked on 27 November 2026 with `gh api repos/<owner>/<repo>` and `gh api repos/<owner>/<repo>/releases/latest`. Claude ran the checks in the main session.

| Repository | Stars | Last push | Latest release | Use in the workshop |
|---|---|---|---|---|
| [grafana/mcp-grafana](https://github.com/grafana/mcp-grafana) | 3,705 | 2026-11-26 | v2.4.0, 2026-11-24 | MCP server for Tempo, Prometheus, Loki, dashboards, alerts |
| [event-catalog/mcp-server](https://github.com/event-catalog/mcp-server) | 24 | 2026-10-08 | v2.1.0, 2025-12-09 | Archived. Replaced by the built-in MCP server of EventCatalog |
| [kurrent-io/mcp-server](https://github.com/kurrent-io/mcp-server) | 11 | 2026-03-31 | no releases | MCP server for KurrentDB streams and projections |
| [Shopify/toxiproxy](https://github.com/Shopify/toxiproxy) | 12,549 | 2026-11-24 | v2.13.0, 2026-10-14 | TCP proxy that injects latency, timeouts, resets |
| [codecentric/chaos-monkey-spring-boot](https://github.com/codecentric/chaos-monkey-spring-boot) | 944 | 2026-11-23 | v4.0.0, 2026-02-06 | Latency and exception assaults on Spring beans |
| [alexei-led/pumba](https://github.com/alexei-led/pumba) | 3,187 | 2026-11-23 | 1.6.0, 2026-11-05 | Kills, pauses, slows Docker containers |
| [open-telemetry/opentelemetry-demo](https://github.com/open-telemetry/opentelemetry-demo) | 3,477 | 2026-11-26 | 3.1.0, 2026-09-18 | Reference for failure scenarios behind feature flags |
| [grafana/docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm) | 2,174 | 2026-11-27 | v0.41.2, 2026-11-27 | The sample uses 0.41.0 |
| [grafana/intro-to-mltp](https://github.com/grafana/intro-to-mltp) | 670 | 2026-11-26 | no releases | Grafana's own observability workshop stack |
| [dastergon/wheel-of-misfortune](https://github.com/dastergon/wheel-of-misfortune) | 202 | 2024-02-27 | 5.0, 2020-08-04 | Incident role-play game, after the Google SRE book |
| [kafbat/kafka-ui](https://github.com/kafbat/kafka-ui) | 2,863 | 2026-11-23 | v1.5.1, 2026-11-20 | Web UI for Kafka topics, consumer groups, lag |
| [redpanda-data/console](https://github.com/redpanda-data/console) | 4,354 | 2026-11-26 | v3.15.0, 2026-11-18 | Web UI for Kafka, works with any Kafka |
| [microsoft/playwright-mcp](https://github.com/microsoft/playwright-mcp) | 40,199 | 2026-11-18 | v0.0.86, 2026-11-18 | MCP server that drives a browser |
| [confluentinc/mcp-confluent](https://github.com/confluentinc/mcp-confluent) | 171 | 2026-11-27 | v1.6.1, 2026-10-26 | Confluent's MCP server for Kafka |
| [kroxylicious/kroxylicious](https://github.com/kroxylicious/kroxylicious) | 336 | 2026-11-27 | v0.26.0, 2026-11-19 | Kafka protocol proxy with filters |

## Notes from web search, to check against the primary source

- EventCatalog: "Every EventCatalog instance now includes a built-in MCP server at /docs/mcp/." It needs SSR mode and a Scale licence. The standalone package will be deprecated. Sources: <https://eventcatalog.dev/blog-2/built-in-mcp-server>, <https://eventcatalog.dev/blog/eventcatalog-mcp>. The archived repo above confirms the deprecation.
- KurrentDB MCP server: launched in May 2025, MIT licence, configured with `KURRENTDB_CONNECTION_STRING`, needs `--run-projections=all` and `--start-standard-projections`. Source: <https://www.businesswire.com/news/home/20250528051741/en>. The sample's Compose file already sets `KURRENTDB_RUN_PROJECTIONS=All` and `KURRENTDB_START_STANDARD_PROJECTIONS=true`.
- OpenTelemetry demo flags: `paymentFailure` (error for a share of charge calls), `kafkaQueueProblems` (consumer delay, lag grows), `cartFailure`, `adFailure`, `adHighCpu`, `loadGeneratorVUs`, and newer AI flags. Source: <https://opentelemetry.io/docs/demo/feature-flags/>.
- Wheel of Misfortune: a role-play exercise from Google SRE, described in the SRE book chapter "Accelerating SREs to On-Call and Beyond". GOV.UK PaaS wrote up how they ran it: <https://technology.blog.gov.uk/2021/03/04/wheel-of-misfortune-incident-rehearsal-for-gov-uk-paas/>.

## Chaos Monkey for Spring Boot: how it decides what to attack

- v4.0.0 is "Built with Spring Boot 4.0.2" (release notes). The sample uses Spring Boot 4.1.2 (`samples/e-commerce/build.gradle:6`). Compatibility with 4.1 is not confirmed.
- `chaos.monkey.assaults.level`: "1 each request, 5 each 5th request is attacked". `chaos.monkey.assaults.deterministic`: "Whether the level should be used as a deterministic value (attack every x requests) or a chance (on average, 1 in x requests will be attacked)". Source: [configuration.adoc](https://github.com/codecentric/chaos-monkey-spring-boot/blob/main/chaos-monkey-docs/src/main/asciidoc/configuration.adoc).
- Watchers pick the attacked code by Spring stereotype or by `chaos.monkey.watcher.beans` / `beanClasses`. Assaults are latency, exceptions, killing the application, memory and CPU.
- Consequence for the sample: "deterministic" means "every Nth call", counted at run time. With Kafka and several consumers, the Nth call is a different message on each run, so participants would not see the same incident. A repeatable incident needs a decision keyed on a business identifier, such as the order id or the message type, not on a call counter.
- The sample's modules register handlers through `messageBus.subscribe(...)` in their `Config` classes, not as annotated beans, so the watchers can only attack the facades and gateways as beans, not a single message type.
