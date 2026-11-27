# Tools for the workshop

After Q19 in `../qa.md`. Agreed: KurrentDB MCP left out of version 1 (Q17); Spring MCP with three tools in version 1.0 (Q19). The rest is still a proposal. Facts checked on 27 November 2026; versions in `../tools-and-versions/findings.md`.

## In the browser, no install

| Tool | What for | Status |
|---|---|---|
| Shop, sandbox payment page, sandbox warehouse | Use the system, cause failures by hand | in the sample |
| Swagger UI | Call the web API, `ETag`, `If-Match`, `If-None-Match` | in the sample |
| Springwolf UI | Read the AsyncAPI documents, **Publish** messages to the bus | in the sample |
| EventCatalog | Find who sends and receives what, context map, flows, dictionary | in the sample; served as a static site from a container in the workshop profile |
| Grafana (Tempo, Prometheus, Loki), 3 dashboards, 8 alerts | Traces, metrics, logs, alerts | in the sample (`grafana/otel-lgtm`) |
| Kafbat UI ([kafbat/kafka-ui](https://github.com/kafbat/kafka-ui), Apache-2.0, v1.5.1) | Topics, messages with headers, consumer groups with lag per partition, produce a message; runs as `ghcr.io/kafbat/kafka-ui` | to add; its default port 8080 clashes with the app, so it needs another host port |
| pgAdmin | Events (`messages`, with `correlationId`, `causationId`, `traceId`, `spanId` in `message_metadata`), views, `processor_checkpoints` | in Compose under `postgres-ui`; to add to the workshop profile |
| KurrentDB UI | Streams, event metadata (`$correlationId`, `$causationId`) | built into KurrentDB, port 2113; an option only, since the workshop profile uses the PostgreSQL event store (Q23) |

## MCP servers for the LLM

| Server | What for | How it runs | Open points |
|---|---|---|---|
| Grafana MCP ([grafana/mcp-grafana](https://github.com/grafana/mcp-grafana), official) | TraceQL, PromQL, LogQL, dashboards, alert rules | Docker image `grafana/mcp-grafana` with `-t stdio` (README example); `--disable-write` for read-only | Needs a token or user and password; otel-lgtm has anonymous Admin, untested whether the server works without credentials |
| KurrentDB MCP ([kurrent-io/mcp-server](https://github.com/kurrent-io/mcp-server), official) | Read streams and event metadata | A Python script (`server.py`, `requirements.txt`, started with `python`); no Docker image and no releases | Participants would need Python, or we package it in an image; 11 stars, last push March 2026 |
| EventCatalog | Who sends and receives what | No MCP without a Scale licence | The LLM client reads `eventcatalog/specs/*.json` and `eventcatalog/domains/` as files |
| Spring MCP (ours) | Read a stream with its events' metadata; the subscriptions with their checkpoints; the timeline joining events and spans per order | In the app, Spring AI 2.0.3 MCP server over HTTP | Version 1.0 (Q19); read-only; must not reveal the active scenario |

LLM clients (Q27): the sample ships MCP config for Claude Code, Codex and OpenCode. Checked on 27 November 2026:

- Claude Code: `.mcp.json` in the project.
- Codex: `.codex/config.toml` in the project, with an `[mcp_servers.<name>]` table. Codex loads project config only for trusted projects; an untrusted project ignores it (<https://developers.openai.com/codex/mcp>, <https://developers.openai.com/codex/config-advanced>). The repo root already has a `.codex` folder.
- OpenCode: `opencode.json` with an `mcp` key; a server is `type: "local"` with a `command`, or `type: "remote"` with a `url` (<https://opencode.ai/docs/mcp-servers/>). OpenCode's v2 docs nest servers under `mcp.servers`, so the format depends on the version (<https://opencode.ai/v2/docs/mcp-servers/>). The repo root already has a `.opencode` folder.

Grafana MCP runs through `docker`, and our Spring MCP is reached by URL, so nobody installs Node, Python or Go.

## KurrentDB MCP: value against effort

Read from `server.py` in [kurrent-io/mcp-server](https://github.com/kurrent-io/mcp-server) on 27 November 2026:

- Dependencies: `kurrentdbclient>=1.0.6` and `fastmcp>=2.12.3`. A Docker image of our own is about five lines (Python slim image, `pip install -r requirements.txt`, run `server.py`), built by Compose from a pinned commit, since the repo has no releases. So nobody needs Python.
- `read_stream` returns, for each event, only its type and its data ("An event of type: … has occurred with details: …"). It doesn't return the metadata, so no `$correlationId` or `$causationId`. The correlation exercise and the incidents need exactly those.
- It also has write tools: `write_events_to_stream`, `create_projection`, `update_projection`. There is no read-only switch, so an LLM could append events to the shop's streams during an exercise.
- The connection string comes from `KURRENTDB_CONNECTION_STRING`, or from a shell command in `KURRENTDB_CONNECTION_COMMAND`.

Conclusion: packaging is cheap, but the value is low for these exercises, and the write tools are a risk. Decided in Q17: left out of version 1. Our Spring MCP reads the events with their metadata, read-only.

## A Spring MCP server

Checked on 27 November 2026:

- [spring-projects/spring-ai](https://github.com/spring-projects/spring-ai), 9,694 stars, Apache-2.0. v2.1.0 (2026-11-20) is built on Spring Boot 4.2.0; v2.0.3 (2026-11-20) on Spring Boot 4.1.3 (`<spring-boot.version>` in each tag's `pom.xml`). The sample uses Spring Boot 4.1.2, so v2.0.3 fits without a Boot upgrade.
- Spring AI's MCP server starters expose Spring beans as MCP tools over STDIO, SSE or Streamable HTTP (<https://docs.spring.io/spring-ai/reference/api/mcp/mcp-stdio-sse-server-boot-starter-docs.html>). Inside the app, the server runs on the app's port, so the LLM client connects by URL and nobody installs anything.
- Nothing in Spring AI turns Actuator endpoints into MCP tools; the app writes its own tools. The third-party [Dmitriusan/mcp-spring-boot-actuator](https://github.com/Dmitriusan/mcp-spring-boot-actuator) has 1 star and runs on Node; not worth it.
- The app has `spring-boot-starter-actuator` (`build.gradle`) and sets no `management.endpoints` exposure, so only the defaults are exposed.

Tools that would add something Grafana MCP can't give, all read-only:

- read a stream with its events' metadata (`$correlationId`, `$causationId`): the part the KurrentDB MCP lacks;
- the subscriptions and their checkpoints from `processor_checkpoints`, with the event at the checkpoint: which subscription is stuck and on what (AC 4.4);
- the timeline that joins events and spans per order. All three are in version 1.0 (Q19).

A tool must not reveal the active scenario (for example through `env` or `configprops`), or the incidents are solved by asking.

## Left out, and why

- Toxiproxy, Pumba, Chaos Monkey for Spring Boot: the scenario decorators and `docker compose stop` cover what we need (`../fault-injection/findings.md`).
- Redpanda Console: one Kafka UI is enough. Kafbat UI has a clear Apache-2.0 licence; Redpanda Console's licence wasn't checked.
- Playwright MCP, Confluent MCP, a PostgreSQL MCP: not needed for the exercises; pgAdmin and the Kafka UI cover what attendees look at.
