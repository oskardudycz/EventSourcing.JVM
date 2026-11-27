# Running the sample without Java, locally

Checked on 27 November 2026 by Claude in the main session. Codespaces and other cloud environments are out of scope (`../qa.md`). Participants need Docker only.

## What runs today

- `./gradlew bootRun` needs a JDK 17 or newer to start Gradle; Gradle then uses or downloads JDK 22 (`HOWTO.md`, "What you need"). So today a participant without Java can't start the app.
- The Compose file (`docker-compose.yml`) has KurrentDB, PostgreSQL, Kafka, RabbitMQ and Grafana LGTM, but not the app. `bootRun` starts Compose through Spring Boot's Docker Compose support, with profiles `sample,${messaging.bus}` (`application.properties`).
- EventCatalog is built by Gradle with a downloaded Node.js (`build.gradle`, `eventCatalog*` tasks), and served by `eventCatalogPreview` on port 3001.

## Images and CPU architecture

Checked with `docker manifest inspect` on the tags in `docker-compose.yml`:

| Image | Architectures |
|---|---|
| `kurrentplatform/kurrentdb:26.2.3` | one architecture only (single manifest, not a list) |
| `kurrentplatform/kurrentdb:26.2.3-experimental-arm64-10.0-noble` | separate ARM64 tag (single manifest); the Compose file comment already says to use it on Apple Silicon |
| `confluentinc/confluent-local:8.4.1` | amd64, arm64 |
| `postgres:18.7-alpine` | amd64, arm64 and more |
| `grafana/otel-lgtm:0.41.0` | amd64, arm64 |

Web search found no multi-arch manifest for KurrentDB 26 on Docker Hub, and the Red Hat catalog lists amd64 only (<https://hub.docker.com/r/kurrentplatform/kurrentdb/tags>, <https://catalog.redhat.com/en/software/containers/kurrent-io/kurrentdb/690a11e9b1dedf2b2890ef75>). So a Mac with Apple Silicon needs `KURRENTDB_IMAGE=...-experimental-arm64-10.0-noble`, or the in-memory or PostgreSQL event store.

## Packaging the app as an image

- Spring Boot's `bootBuildImage` builds an OCI image with Paketo buildpacks. `imagePlatform` sets the OS and architecture, such as `linux/arm64` (<https://docs.spring.io/spring-boot/4.2/gradle-plugin/packaging-oci-image.html>). Since Spring Boot 3.4, two runs with different `imagePlatform` values and one manifest give a multi-arch image (DaShaun Carter's post: <https://dev.to/dashaun/multi-architecture-spring-oci-from-anywhere-with-paketo-1ic2>). The default builder is `paketobuildpacks/builder-noble-java-tiny` (Spring Boot 4.0 Maven docs).
- Not checked yet: Jib, and a plain multi-stage Dockerfile.
- Building the image needs a JDK, so the trainer builds and publishes it (for example to GHCR from CI), and participants pull it.

Changes the app needs inside Compose (from `application.properties`): host names instead of `localhost` for PostgreSQL, KurrentDB, Kafka and the OTLP endpoint; Kafka has a listener for other containers already (`PLAINTEXT://kafka:29092` in `docker-compose.yml`); and Spring Boot's Docker Compose support turned off in the container.

## EventCatalog without Node

- EventCatalog exports a static site to `dist` and can be served by any web server. Its docs show a Dockerfile flow: `docker build -t eventcatalog .` and `docker run -p 3000:80 -it eventcatalog` (<https://www.eventcatalog.dev/docs/development/deployment/hosting-options.md>, "Hosting static website with Docker").
- The sample uses `@eventcatalog/core` 5.12.0 with `base: '/'` (`eventcatalog/package.json`, `eventcatalog/eventcatalog.config.js`), so the static site can be served from an nginx container on its own port.
- SSR mode needs a Node server; it is required only for EventCatalog's built-in MCP server (Scale licence, see `../tools-and-versions/findings.md`).

## Options

1. One `workshop` Compose profile with the published app image and a static EventCatalog image next to the existing services. Participants run `docker compose --profile workshop up`. Scenarios are selected with an environment variable on the app container.
2. Same as 1, plus `./gradlew bootRun --args='--workshop.scenario=...'` for those who have a JDK and want to read or change the code.
