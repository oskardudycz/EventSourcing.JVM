[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/oskardudycz/)  [![Github Sponsors](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&link=https://github.com/sponsors/oskardudycz/)](https://github.com/sponsors/oskardudycz/) [![blog](https://img.shields.io/badge/blog-event--driven.io-brightgreen)](https://event-driven.io/?utm_source=event_sourcing_jvm) [![blog](https://img.shields.io/badge/%F0%9F%9A%80-Architecture%20Weekly-important)](https://www.architecture-weekly.com/?utm_source=event_sourcing_jvm)

![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_build-your-own-event-store-exercises.yml/badge.svg?branch=main) ![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_build-your-own-event-store-solved.yml/badge.svg?branch=main)

# Build Your Own Event Store Self-Paced Kit

You can watch:

<a href="https://www.youtube.com/watch?v=gaoZdtQSOTo&list=PLw-VZz_H4iiqUeEBDfGNendS0B3qIk-ps&index=2" target="_blank"><img src="https://img.youtube.com/vi/gaoZdtQSOTo/0.jpg" alt="Let's build event store in one hour!" width="640" height="480" border="10" /></a>

and read:

- 📝 [Let's build event store in one hour!](https://event-driven.io/en/lets_build_event_store_in_one_hour/?utm_source=event_sourcing_jvm)

## Description

**Event Sourcing basics** - it teaches the event store basics by showing how to build your own Event Store on a relational database. It starts with the tables setup, goes through appending events and event store methods, and finishes with stream aggregation and time travelling.

## Exercises

1. [Streams and Events Tables](./exercises/src/test/java/io/eventdriven/buildyourowneventstore/e01_storage/postgresql)
2. [Appending Events](./exercises/src/test/java/io/eventdriven/buildyourowneventstore/e03_create_append_event_function)
3. [Event Store Methods](./exercises/src/test/java/io/eventdriven/buildyourowneventstore/e04_event_store_methods)
4. [Stream Aggregation](./exercises/src/test/java/io/eventdriven/buildyourowneventstore/e05_stream_aggregation)
5. [Time Travelling](./exercises/src/test/java/io/eventdriven/buildyourowneventstore/e06_time_travelling)

The [solved](./solved/) project has the solutions, plus MongoDB variants of the storage and event store methods:

1. Streams and Events Tables: [PostgreSQL](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e01_storage/postgresql), [MongoDB](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e01_storage/mongodb)
2. Appending Events: [single event](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e02_append_events/postgresql/single_event), [multiple events](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e02_append_events/postgresql/multiple_events)
3. Event Store Methods: [PostgreSQL](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e04_event_store_methods/postgresql), [MongoDB](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e04_event_store_methods/mongodb)
4. [Stream Aggregation](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e05_stream_aggregation)
5. [Time Travelling](./solved/src/test/java/io/eventdriven/buildyourowneventstore/e06_time_travelling)

## Prerequisites

1. Install git - https://git-scm.com/downloads.
2. Clone this repository.
3. Install Java JDK 22 or newer - https://www.oracle.com/java/technologies/downloads/. The build compiles for Java 22 and runs on the JDK you start Gradle with. CI checks JDK 22, 25 and 27.
4. Install IntelliJ, Eclipse, VSCode or other preferred IDE.
5. Install docker - https://docs.docker.com/engine/install/.
6. Open [exercises](./exercises/) folder as project.

## Running

1. Make sure Docker is running. The tests start PostgreSQL (and MongoDB in the solved project) with [Testcontainers](https://testcontainers.com/).
2. Open, build and run the project, or run `./gradlew test` in the `exercises` or `solved` folder.
3. To see solved exercises, open [solved](./solved/) folder as project.
4. To browse data in the database UIs, run `docker compose --profile all up` in the `exercises` or `solved` folder:
   - [exercises/docker-compose.yml](./exercises/docker-compose.yml) starts PostgreSQL on port 5432 and pgAdmin at http://localhost:5050/.
   - [solved/docker-compose.yml](./solved/docker-compose.yml) also starts MongoDB on port 27017 and Mongo Express at http://localhost:8081/.
   - pgAdmin login is `admin@pgadmin.org` with password `admin`. To connect to the database, use host `postgres`, user `postgres` and password `postgres`.
   - If a port is already taken, for example by another workshop, set its variable: `POSTGRES_PORT`, `PGADMIN_PORT`, `MONGODB_PORT` or `MONGO_EXPRESS_PORT`, e.g. `MONGO_EXPRESS_PORT=8082 docker compose --profile all up`.
5. To see the test data in these UIs, start docker compose: run `docker compose --profile all up`. Any compose file from this repository works, because they all define the same `eventsourcing` project. The tests then use those containers automatically, with no extra settings, even if you changed a port. Stop docker compose to go back to Testcontainers.
6. Run `docker compose --profile all down -v` to stop the containers and remove their data.
