[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/oskardudycz/)  [![Github Sponsors](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&link=https://github.com/sponsors/oskardudycz/)](https://github.com/sponsors/oskardudycz/) [![blog](https://img.shields.io/badge/blog-event--driven.io-brightgreen)](https://event-driven.io/?utm_source=event_sourcing_jvm) [![blog](https://img.shields.io/badge/%F0%9F%9A%80-Architecture%20Weekly-important)](https://www.architecture-weekly.com/?utm_source=event_sourcing_jvm)

![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_event-driven-architecture-exercises.yml/badge.svg?branch=main) ![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_event-driven-architecture-solved.yml/badge.svg?branch=main)

# Event-Driven Architecture Workshop

Event-Driven Architecture sounds like a buzzword, but it's a practical way to build systems where modules react to what happened instead of calling each other directly. It's also easy to get wrong: lost messages, broken processes and no idea why a customer ended up in a weird state.

The workshop builds that knowledge step by step on a single domain, a hotel's guest stays and group checkouts. You start with modelling events and entities, then coordinate a business process across modules, first in memory and then on real message brokers.

1. Events and entities: modelling what happened and the state that decides what can happen next.
2. Commands and events: intent versus facts, and why they can travel through the same message bus.
3. Business processes: transactions, choreography, todo list, process managers and sagas, with their trade-offs.
4. Messaging on Kafka and RabbitMQ: running the same saga on a real broker.
5. Message metadata: correlation and causation IDs that let you follow a process across messages.
6. Handling failures: retries, dead-letter queues, unreadable messages, and deciding what to do with each.
7. Observability: following a business process end to end with OpenTelemetry traces and metrics.

You can do the workshop as a self-paced kit. If you'd like to get full coverage with all nuances of the private workshop, feel free to contact me via [email](mailto:oskar.dudycz@gmail.com).

## Exercises

1. [Events definition](./exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/e01_events_definition).
2. [Entities definition](./exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/e02_entities_definition).
3. [Business processes](./exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses).
4. [Idempotency](./exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/e04_idempotency).
5. [Optimistic concurrency](./exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/e05_optimistic_concurrency).
6. [Consistency](./exercises/src/test/java/io/eventdriven/eventdrivenarchitecture/e06_consistency).

Each exercise has a full implementation in the [solved](./solved/) folder. Some go further than the exercise:

- [Business processes](./solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses): transactions, choreography, todo list, process managers and sagas, plus sagas on Kafka and RabbitMQ with retries, dead-letter queues and tracing you can watch in Grafana.

## Prerequisites

1. Install git - https://git-scm.com/downloads.
2. Clone this repository.
3. Install Java JDK 22 or newer - https://www.oracle.com/java/technologies/downloads/. The build compiles for Java 22 and runs on the JDK you start Gradle with. CI checks JDK 22, 25 and 27.
4. Install IntelliJ, Eclipse, VSCode or other preferred IDE.
5. Install docker - https://docs.docker.com/engine/install/.
6. Open [exercises](./exercises/) folder as project.

## Running

1. Open, build and run the project. The Kafka and RabbitMQ tests start their brokers with Testcontainers, so Docker has to be running. If `docker compose` is running, the tests use its brokers instead.
2. To see solved exercises, open the [solved](./solved/) folder as a project.
3. Optionally, run `docker compose --profile all up` in this folder. It starts PostgreSQL, pgAdmin at http://localhost:5050/, Kafka, RabbitMQ with its management UI at http://localhost:15672/ and [Grafana LGTM](https://github.com/grafana/docker-otel-lgtm) at http://localhost:3000/. The solved tests always send traces and metrics to it. If it isn't running, they still pass and don't log export errors. To see the traces, follow [Seeing it](./solved/src/test/java/io/eventdriven/eventdrivenarchitecture/e03_businessprocesses/README.md#seeing-it).
4. If a port is already taken, set its variable: `POSTGRES_PORT`, `PGADMIN_PORT`, `KAFKA_PORT`, `RABBITMQ_PORT`, `RABBITMQ_MANAGEMENT_PORT`, `GRAFANA_PORT`, `OTEL_GRPC_PORT` or `OTEL_HTTP_PORT`, e.g. `GRAFANA_PORT=3001 docker compose --profile all up`. If you change `OTEL_HTTP_PORT`, set the same variable for the test run, e.g. `OTEL_HTTP_PORT=54318 ./gradlew test`. The tests read it in [application.properties](./solved/src/test/resources/application.properties).
