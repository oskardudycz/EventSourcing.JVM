[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/oskardudycz/)  [![Github Sponsors](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&link=https://github.com/sponsors/oskardudycz/)](https://github.com/sponsors/oskardudycz/) [![blog](https://img.shields.io/badge/blog-event--driven.io-brightgreen)](https://event-driven.io/?utm_source=event_sourcing_jvm) [![blog](https://img.shields.io/badge/%F0%9F%9A%80-Architecture%20Weekly-important)](https://www.architecture-weekly.com/?utm_source=event_sourcing_jvm) 

![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/samples_event-sourcing-kurrentdb-simple.yml/badge.svg?branch=main) 

# Event Sourcing with Spring Boot and KurrentDB

Sample is showing basic Event Sourcing flow. It uses [KurrentDB](https://docs.kurrent.io/) for event storage and [Spring Data JPA](https://spring.io/projects/spring-data-jpa) backed with [PostgreSQL](https://www.postgresql.org/) for read models. 

The presented use case is Shopping Cart flow:
1. The customer may add a product to the shopping cart only after opening it.
2. When selecting and adding a product to the basket customer needs to provide the quantity chosen. The product price is calculated by the system based on the current price list.
3. The customer may remove a product with a given price from the cart.
4. The customer can confirm the shopping cart and start the order fulfilment process.
5. The customer may also cancel the shopping cart and reject all selected products.
6. After shopping cart confirmation or cancellation, the product can no longer be added or removed from the cart.

Technically it's modelled as Web API written in [Spring Boot](https://spring.io/projects/spring-boot) and [Java 22](https://www.oracle.com/java/technologies/downloads/). 

## Watch the video

Practical introduction to Event Sourcing with Spring Boot and EventStoreDB

<a href="https://www.youtube.com/watch?v=LaUSPtwFLSg" target="_blank"><img src="https://img.youtube.com/vi/LaUSPtwFLSg/0.jpg" alt="Practical introduction to Event Sourcing with Spring Boot and EventStoreDB" width="320" height="240" border="10" /></a>

## Main assumptions
- explain basics of Event Sourcing, both from the write model ([KurrentDB](https://docs.kurrent.io/)) and read model part ([PostgreSQL](https://www.postgresql.org/) and [Spring Data JPA](https://spring.io/projects/spring-data-jpa)),
- present that you can join classical approach with Event Sourcing without making a massive revolution,
- [CQRS](https://event-driven.io/en/cqrs_facts_and_myths_explained/) architecture sliced by business features, keeping code that changes together at the same place. Read more in [How to slice the codebase effectively?](https://event-driven.io/en/how_to_slice_the_codebase_effectively/),
- clean, composable (pure) functions for command, events, projections, query handling, minimising the need for marker interfaces. Thanks to that testability and easier maintenance.
- easy to use and self-explanatory fluent API for registering commands and projections with possible fallbacks,
- registering everything into regular DI containers to integrate with other application services.
- pushing the type/signature enforcement on edge, so when plugging to DI.

## Overview

It uses:
- pure data entities, functions and handlers,
- Stores events from the command handler result KurrentDB,
- Builds read models using [Subscription to `$all`](https://docs.kurrent.io/clients/java/v1.2/subscriptions.html#basic-subscriptions).
- Read models are stored to Postgres relational tables with [Spring Data JPA](https://spring.io/projects/spring-data-jpa).
- App has Swagger and predefined [docker-compose](./docker-compose.yml) to run and play with samples.

## Write Model
- Sample [ShoppingCart](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCart.java) entity and [events](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartEvent.java) represent the business workflow. All events are stored in the same file using [sealed classes](https://blogs.oracle.com/javamagazine/post/java-sealed-classes-fight-ambiguity) to be able to understand flow without jumping from one file to another. It also enables better typing support in pattern matching. `ShoppingCart` also contains [when method](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCart.java#L40) method defining how to apply events to get the entity state. It uses the Java 22 switch syntax for pattern matching.
- [Commands](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartCommand.java) are also grouped in a single file as sealed interface. Business logic is in [ShoppingCartDecider](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartDecider.java). Its methods take command and state and create new events based on the business logic. See sample [Adding Product Item to ShoppingCart](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartDecider.java#L43). This example also shows that you can inject external services to the decider if needed.
- Code uses [functional interfaces](https://www.theserverside.com/blog/Coffee-Talk-Java-News-Stories-and-Opinions/Get-the-most-from-Java-Function-interface-with-this-example) in many places to introduce composability and loosely coupled, testable code.
- Added generic [CommandHandler](./src/main/java/io/eventdriven/ecommerce/core/entities/CommandHandler.java) to load entity state from KurrentDB, run business logic and append the created event. It is configured for shopping carts in [ShoppingCartsConfig](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartsConfig.java).
- Commands and queries have separate endpoints, see [ShoppingCartsCommandController](./src/main/java/io/eventdriven/ecommerce/api/controllers/ShoppingCartsCommandController.java) and [ShoppingCartsQueryController](./src/main/java/io/eventdriven/ecommerce/api/controllers/ShoppingCartsQueryController.java).
- All command handling has to support [optimistic concurrency](https://event-driven.io/en/optimistic_concurrency_for_pessimistic_times/). Implemented full flow using [If-Match header](./src/main/java/io/eventdriven/ecommerce/api/controllers/ShoppingCartsCommandController.java#L66) and returning [ETag](./src/main/java/io/eventdriven/ecommerce/core/http/ETag.java). Read more details in my article [How to use ETag header for optimistic concurrency](https://event-driven.io/en/how_to_use_etag_header_for_optimistic_concurrency/)

## Read Model
- Read models are rebuilt with eventual consistency using [subscribe to $all stream](https://docs.kurrent.io/clients/java/v1.2/subscriptions.html#basic-subscriptions) KurrentDB feature,
- Used [Spring Data JPA](https://spring.io/projects/spring-data-jpa) to store projection data into Postgres tables.
- Added sample projection for [Shopping cart details](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/ShoppingCartDetailsProjection.java) and slimmed [Shopping cart short info](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingcarts/ShoppingCartShortInfo.java) as an example of different interpretations of the same events. Shopping cart details also contain a nested collection of product items to show more advanced use case. Added also base [JPAProjection class](./src/main/java/io/eventdriven/ecommerce/core/projections/JPAProjection.java) to reduce needed boilerplate. It supports idempotency by checking the last processed event position against the read model.
- Projections are registered as Spring Boot [event listeners](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/ShoppingCartDetailsProjection.java). Which enables reusing the generic mechanism for publishing events from KurrentDB,
- Added long-polling example with [If-None-Match](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-None-Match) header handling for getting shopping cart details by id. See details in [GetShoppingCartById](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/GetShoppingCartById.java#L19) and [ShoppingCartDetailsRepository](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/ShoppingCartDetailsRepository.java#L13)
- Used service [KurrentDBSubscriptionToAll](./src/main/java/io/eventdriven/ecommerce/core/subscriptions/KurrentDBSubscriptionToAll.java) to handle subscribing to all. It handles checkpointing and resubscribing when the connection is dropped. Added also general [background worker](./src/main/java/io/eventdriven/ecommerce/api/backgroundworkers/KurrentDBSubscriptionBackgroundWorker.java) to wrap the general [Smart Lifecycle](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/SmartLifecycle.html) handling.
- KurrentDB subscription forwards events to the Spring Boot [ApplicationEventPublisher](https://reflectoring.io/spring-boot-application-events-explained/) through [EventForwarder](./src/main/java/io/eventdriven/ecommerce/core/events/EventForwarder.java) wrapping them with [EventEnvelope](./src/main/java/io/eventdriven/ecommerce/core/events/EventEnvelope.java) to pass theirs metadata.
- [example query handlers](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/GetShoppingCartById.java) for reading data.
- Used checkpointing to KurrentDB stream with [KurrentDBSubscriptionCheckpointRepository](./src/main/java/io/eventdriven/ecommerce/core/subscriptions/KurrentDBSubscriptionCheckpointRepository.java).

### Testing
- Added API integration tests together with a simple wrapper with Specification Pattern to make tests cleaner using Given/When/Then approach. See more in [ApiSpecification](./src/test/java/io/eventdriven/ecommerce/testing/ApiSpecification.java). See example tests in [AddProductItemToShoppingCartTests](./src/test/java/io/eventdriven/ecommerce/api/controller/AddProductItemToShoppingCartTests.java).
- Added basic unit tests for state rebuild in [ShoppingCartTests](./src/test/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartTests.java)
- API tests start KurrentDB and PostgreSQL with [Testcontainers](https://testcontainers.com/), so running them needs only Docker. `docker compose --profile all up` is needed to run the application. While it's running, the tests use its containers instead of starting their own.


## Prerequisites

1. Install git - https://git-scm.com/downloads.
2. Install Java JDK 22 or newer - https://www.oracle.com/java/technologies/downloads/. The build compiles and tests with the JDK you start Gradle with. If that JDK is older than 22 (Gradle needs at least 17), Gradle uses an installed JDK 22 or downloads it. CI checks JDK 22, 25 and 27.
3. Install IntelliJ, Eclipse, VSCode or other preferred IDE.
4. Install docker - https://docs.docker.com/engine/install/.
5. Open project folder.

## Running

1. Run: `docker compose --profile all up`. On ARM (e.g. Apple Silicon) run `KURRENTDB_IMAGE=kurrentplatform/kurrentdb:26.2.3-experimental-arm64-10.0-noble docker compose --profile all up` instead.
2. Wait until all containers are downloaded and running.
3. You should automatically get:
    - KurrentDB UI (for event store): http://localhost:2113/
    - Postgres DB running (for read models)
    - PG Admin - IDE for postgres. Available at: http://localhost:5050.
        - Login: `pgadmin4@pgadmin.org`, Password: `admin`
        - To connect to server Use host: `postgres`, user: `postgres`, password: `postgres`
    - If a port is already taken, set `KURRENTDB_PORT`, `POSTGRES_PORT` or `PGADMIN_PORT`, e.g. `PGADMIN_PORT=5051 docker compose --profile all up`. If you change `KURRENTDB_PORT` or `POSTGRES_PORT`, set the same variable when you start the application, e.g. `POSTGRES_PORT=55432 docker compose --profile all up` and `POSTGRES_PORT=55432 ./gradlew bootRun`. [application.properties](./src/main/resources/application.properties) reads these variables and falls back to the default ports.
4. Open, build and run `ECommerceApplication`.
    - Swagger should be available at: http://localhost:8080/swagger-ui/index.html
