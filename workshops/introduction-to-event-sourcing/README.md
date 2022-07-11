[![Twitter Follow](https://img.shields.io/twitter/follow/oskar_at_net?style=social)](https://twitter.com/oskar_at_net) [![Github Sponsors](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&link=https://github.com/sponsors/oskardudycz/)](https://github.com/sponsors/oskardudycz/) [![blog](https://img.shields.io/badge/blog-event--driven.io-brightgreen)](https://event-driven.io/?utm_source=event_sourcing_jvm) [![blog](https://img.shields.io/badge/%F0%9F%9A%80-Architecture%20Weekly-important)](https://www.architecture-weekly.com/?utm_source=event_sourcing_jvm) 

![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_introduction-to-event-sourcing-exercises.yml/badge.svg?branch=main) ![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/workshops_introduction-to-event-sourcing-solved.yml/badge.svg?branch=main) 

# Introduction to Event Sourcing Workshop

Event Sourcing is perceived as a complex pattern. Some believe that it's like Nessie, everyone's heard about it, but rarely seen it. In fact, Event Sourcing is a pretty practical and straightforward concept. It helps build predictable applications closer to business. Nowadays, storage is cheap, and information is priceless. In Event Sourcing, no data is lost. 

The workshop aims to build the knowledge of the general concept and its related patterns for the participants. The acquired knowledge will allow for the conscious design of architectural solutions and the analysis of associated risks. 

The emphasis will be on a pragmatic understanding of architectures and applying it in practice using Marten and EventStoreDB.

1. Introduction to Event-Driven Architectures. Differences from the classical approach are foundations and terminology (event, event streams, command, query).
2. What is Event Sourcing, and how is it different from Event Streaming. Advantages and disadvantages.
3. Write model, data consistency guarantees on examples from Marten and EventStoreDB.
4. Various ways of handling business logic: Aggregates, Command Handlers, functional approach.
5. Projections, best practices and concerns for building read models from events on the examples from Marten and EventStoreDB.
6. Challenges in Event Sourcing and EDA: deliverability guarantees, sequence of event handling, idempotency, etc.
8. Saga, Choreography, Process Manager,  distributed processes in practice.
7. Event Sourcing in the context of application architecture, integration with other approaches (CQRS, microservices, messaging, etc.).
8. Good and bad practices in event modelling.
9. Event Sourcing on production, evolution, events' schema versioning, etc.

You can do the workshop as a self-paced kit. That should give you a good foundation for starting your journey with Event Sourcing and learning tools like Marten and EventStoreDB. If you'd like to get full coverage with all nuances of the private workshop, feel free to contact me via [email](mailto:oskar.dudycz@gmail.com).

Read also more in my article [Introduction to Event Sourcing - Self Paced Kit](https://event-driven.io/en/introduction_to_event_sourcing/?utm_source=event_sourcing_jvm).

## Exercises

1. [Events definition](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e01_events_definition).
2. [Getting State from events](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e02_getting_state_from_events).
3. Appending Events:
   * [EventStoreDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e03_appending_event/esdb)
   * _TODO: Axon Server_
4. Getting State from events
   * [EventStoreDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e04_getting_state_from_events)
   * _TODO: Axon Server_
5. Business logic:
   * [General](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e05_business_logic)
   * [EventStoreDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e06_business_logic/esdb)
   * _TODO: Axon Server_
6. Optimistic Concurrency:
   * [EventStoreDB](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e07_optimistic_concurrency/esdb)
   * _TODO: Axon Server_
7. Projections:
   * [General](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e08_projections_singlestream)
   * [Idempotency](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e09_projections_singlestream_idempotency)
   * [Eventual Consistency](./exercises/src/test/java/io/eventdriven/introductiontoeventsourcing/e10_projections_singlestream_eventual_consistency)

## Prerequisites

1. Install git - https://git-scm.com/downloads.
2. Clone this repository.
3. Install Java JDK 17 (or later) - https://www.oracle.com/java/technologies/downloads/.
4. Install IntelliJ, Eclipse, VSCode or other preferred IDE.
5. Install docker - https://docs.docker.com/engine/install/.
6. Open [exercises](./exercises/) folder as project.

## Running

1. Run: `docker-compose up`.
2. Wait until all dockers got are downloaded and running.
3. You should automatically get:
   - EventStoreDB UI: http://localhost:2113/
4. Open, build and run project
5. To see solved exercises, open [solved.sln](./solved/)  folder as project.
