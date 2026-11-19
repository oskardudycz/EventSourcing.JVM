[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/oskardudycz/)  [![Github Sponsors](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&link=https://github.com/sponsors/oskardudycz/)](https://github.com/sponsors/oskardudycz/) [![blog](https://img.shields.io/badge/blog-event--driven.io-brightgreen)](https://event-driven.io/?utm_source=event_sourcing_jvm) [![blog](https://img.shields.io/badge/%F0%9F%9A%80-Architecture%20Weekly-important)](https://www.architecture-weekly.com/?utm_source=event_sourcing_jvm)

![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_introduction-to-event-sourcing-exercises.yml/badge.svg?branch=main) ![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_introduction-to-event-sourcing-solved.yml/badge.svg?branch=main)

# Introduction to Event Sourcing Workshop

Event Sourcing is perceived as a complex pattern. Some believe that it's like Nessie, everyone's heard about it, but rarely seen it. In fact, Event Sourcing is a pretty practical and straightforward concept. It helps build predictable applications closer to business. Nowadays, storage is cheap, and information is priceless. In Event Sourcing, no data is lost.

The workshop aims to build the knowledge of the general concept and its related patterns for the participants. The acquired knowledge will allow for the conscious design of architectural solutions and the analysis of associated risks.

The emphasis will be on a pragmatic understanding of architectures and applying it in practice using Marten and KurrentDB.

1. Introduction to Event-Driven Architectures. Differences from the classical approach are foundations and terminology (event, event streams, command, query).
2. What is Event Sourcing, and how is it different from Event Streaming. Advantages and disadvantages.
3. Write model, data consistency guarantees on examples from Marten and KurrentDB.
4. Various ways of handling business logic: Aggregates, Command Handlers, functional approach.
5. Projections, best practices and concerns for building read models from events on the examples from Marten and KurrentDB.
6. Challenges in Event Sourcing and EDA: deliverability guarantees, sequence of event handling, idempotency, etc.
7. Saga, Choreography, Process Manager, distributed processes in practice.
8. Event Sourcing in the context of application architecture, integration with other approaches (CQRS, microservices, messaging, etc.).
9. Good and bad practices in event modelling.
10. Event Sourcing on production, evolution, events' schema versioning, etc.

You can do the workshop as a self-paced kit. That should give you a good foundation for starting your journey with Event Sourcing and learning tools like Marten and KurrentDB. If you'd like to get full coverage with all nuances of the private workshop, feel free to contact me via [email](mailto:oskar.dudycz@gmail.com).

Read also more in my article [Introduction to Event Sourcing - Self Paced Kit](https://event-driven.io/en/introduction_to_event_sourcing/?utm_source=event_sourcing_jvm).

## Exercises

1. [Events definition](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e01_events_definition).
2. [Getting State from events](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e02_getting_state_from_events).
3. Appending Events:
   - [KurrentDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e03_appending_event/kurrentdb)
   - [MongoDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e03_appending_event/mongodb)
   - _TODO: Axon Server_
4. Getting State from events read from event store:
   - [KurrentDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e04_getting_state_from_events/kurrentdb)
   - [MongoDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e04_getting_state_from_events/mongodb)
   - _TODO: Axon Server_
5. Business logic:
   - [General](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e05_business_logic)
   - [Slimmed](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e06_business_logic_slimmed)
6. Application logic with WebApi in Spring Boot:
   - [KurrentDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e07_application_logic/kurrentdb)
   - [MongoDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e07_application_logic/mongodb)
7. Optimistic Concurrency:
   - [KurrentDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e08_optimistic_concurrency/kurrentdb)
   - [MongoDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e08_optimistic_concurrency/mongodb)
   - _TODO: Axon Server_
8. Projections:
   - [General](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e09_projections_singlestream)
   - [Idempotency](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e10_projections_singlestream_idempotency)
   - [Eventual Consistency](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e11_projections_singlestream_eventual_consistency)
9. Business Processes:
   - [Events definition](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e12_events_definition)
   - [Entities definition](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e13_entities_definition)
   - [Business processes](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e14_businessprocesses)

## Prerequisites

1. Install git - https://git-scm.com/downloads.
2. Clone this repository.
3. Install Java JDK 22 or newer - https://www.oracle.com/java/technologies/downloads/. The build compiles for Java 22 and runs on the JDK you start Gradle with. CI checks JDK 22, 25 and 27.
4. Install IntelliJ, Eclipse, VSCode or other preferred IDE.
5. Install docker - https://docs.docker.com/engine/install/.
6. Open [exercises](./exercises/) folder as project.

## Running

1. Make sure Docker is running. The tests start KurrentDB, PostgreSQL and MongoDB with [Testcontainers](https://testcontainers.com/).
2. Open, build and run project
3. To see solved exercises, open [solved.sln](./solved/) folder as project.
4. To browse data in the database UIs, run `docker compose --profile all up` (or on ARM, e.g. Apple Silicon, `docker compose -f docker-compose.arm.yml --profile all up`) in the `exercises` or `solved` folder. You get KurrentDB UI at http://localhost:2113/, pgAdmin at http://localhost:5050/ and Mongo Express at http://localhost:8081/. If a port is already taken, for example by another workshop, set its variable: `KURRENTDB_PORT`, `POSTGRES_PORT`, `PGADMIN_PORT`, `MONGODB_PORT` or `MONGO_EXPRESS_PORT`, e.g. `MONGO_EXPRESS_PORT=8082 docker compose --profile all up`.
5. To see the test data in these UIs, start docker compose: run `docker compose --profile all up`. Any compose file from this repository works, because they all define the same `eventsourcing` project. The tests then use those containers automatically, with no extra settings, even if you changed a port. Stop docker compose to go back to Testcontainers.
6. To run a Spring Boot application from exercises 07 or 08 by hand, start `docker compose --profile all up` and run its `ECommerceApplication` class from the IDE, or use Gradle with the class name, e.g. `./gradlew bootTestRun -PmainClass=io.eventdriven.introductiontoeventsourcing.e07_application_logic.kurrentdb.immutable.app.ECommerceApplication`. The application uses the localhost settings from [application.properties](./exercises/src/test/resources/application.properties), which read `KURRENTDB_PORT`, `POSTGRES_PORT` and `MONGODB_PORT`. If you changed a database port, set the same variable when you start the application, e.g. `POSTGRES_PORT=55432 ./gradlew bootTestRun -PmainClass=...`. It listens on http://localhost:8080.
7. Run `docker compose --profile all down -v` to stop the containers and remove their data.
