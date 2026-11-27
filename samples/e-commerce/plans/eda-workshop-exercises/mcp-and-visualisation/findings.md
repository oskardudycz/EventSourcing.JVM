# MCP servers and visualisation

Checked on 27 November 2026 by Claude in the main session. Versions and stars are in `../tools-and-versions/findings.md`; the READMEs read are in `../tools-and-versions/sources/`.

## Grafana MCP ([grafana/mcp-grafana](https://github.com/grafana/mcp-grafana), v2.4.0, official)

- Transports: `stdio` (default), `sse`, `streamable-http` (`-t`).
- Tempo tools: `search_tempo_traces` (TraceQL), `query_tempo_metrics`, `get_tempo_trace`, `diff_tempo_traces`, `list_tempo_attribute_names`, `list_tempo_attribute_values`, `get_tempo_traceql_docs`. Also PromQL, LogQL, dashboards and alert rules.
- `--disable-write` gives read-only mode and keeps PromQL, LogQL and TraceQL.
- Auth: `GRAFANA_URL` with a service account token or user and password. The README doesn't describe anonymous access.
- The sample's Grafana is `grafana/otel-lgtm`, which turns on anonymous access with the role `Admin` (`GF_AUTH_ANONYMOUS_ENABLED=true`, `GF_AUTH_ANONYMOUS_ORG_ROLE=Admin` in [run-grafana.sh](https://github.com/grafana/docker-otel-lgtm/blob/main/docker/run-grafana.sh)). The default admin user and password are another way in. Not tested yet whether the MCP server works without a token.
- Datasource uids in otel-lgtm are fixed: `prometheus`, `tempo`, `loki`; Tempo has `serviceMap` and `nodeGraph` set ([grafana-datasources.yaml](https://github.com/grafana/docker-otel-lgtm/blob/main/docker/grafana-datasources.yaml)).
- It sends anonymous usage statistics by default; `--usage-stats=disabled` turns that off.

## KurrentDB MCP ([kurrent-io/mcp-server](https://github.com/kurrent-io/mcp-server), official, no releases)

Reads streams, lists streams, builds and checks projections, writes events. Configured with `KURRENTDB_CONNECTION_STRING`; needs projections on, which the sample's Compose file already sets (`KURRENTDB_RUN_PROJECTIONS=All`, `KURRENTDB_START_STANDARD_PROJECTIONS=true`). Source: README in `../tools-and-versions/sources/kurrent-io_mcp-server.md`, and Kurrent's May 2025 announcement.

## EventCatalog

The built-in MCP server is at `/docs/mcp/`, needs SSR mode and a Scale licence. The standalone `@eventcatalog/mcp-server` repo is archived (<https://eventcatalog.dev/blog-2/built-in-mcp-server>). Without a licence, an LLM can read the AsyncAPI and OpenAPI documents in `eventcatalog/specs/` and the pages in `eventcatalog/domains/` as files.

## Spring Boot

- Spring AI has MCP server starters (STDIO, SSE, Streamable HTTP, stateless) that expose beans as tools (<https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html>, <https://spring.io/blog/2025/09/16/spring-ai-mcp-intro-blog>). Nothing found that exposes Actuator endpoints as tools out of the box; the app would add its own tools.
- A third-party Node server, `dmitriusan/mcp-spring-boot-actuator`, reads Actuator endpoints (health, metrics, env, beans) from outside (<https://mcpservers.org/servers/dmitriusan/mcp-spring-boot-actuator>). Not checked: stars, maintenance. The sample would need its Actuator endpoints exposed.

## Other MCP servers found

Playwright MCP ([microsoft/playwright-mcp](https://github.com/microsoft/playwright-mcp), v0.0.86) drives the shop pages. Confluent's MCP ([confluentinc/mcp-confluent](https://github.com/confluentinc/mcp-confluent), v1.6.1) targets Kafka; not checked whether it works with a local broker without Confluent Cloud.

## Visualisation

- Tempo's service graph pairs spans across services by `service.name`, with edge types `unset`, `virtual_node`, `messaging_system` and `database`; messaging edges come from producer and consumer spans (<https://grafana.com/docs/tempo/latest/metrics-from-traces/service_graphs/>). The sample runs as one service named `e-commerce`, so the graph can show at most one node with edges to Kafka, KurrentDB and PostgreSQL as virtual nodes. It can't show the modules as nodes unless each module reports its own `service.name`.
- Kafka web UIs: Kafbat UI ([kafbat/kafka-ui](https://github.com/kafbat/kafka-ui), v1.5.1) and Redpanda Console ([redpanda-data/console](https://github.com/redpanda-data/console), v3.15.0) show topics, consumer groups and lag, and both run as containers. RabbitMQ's management UI is in the Compose image (`rabbitmq:4.4.0-management`, port 15672). KurrentDB has its own web UI on port 2113.
- EventCatalog draws the context map, the purchase flow and each service's sends and receives (README, "Documenting the messages").
