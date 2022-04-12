# Event Sourcing with Spring Boot and EventStoreDB

Sample is showing basic Event Sourcing flow. It uses [EventStoreDB](https://developers.eventstore.com/) for event storage and [Spring Data JPA](https://spring.io/projects/spring-data-jpa) backed with [PostgreSQL](https://www.postgresql.org/) for read models. 

The presented use case is Shopping Cart flow:
1. The customer may add a product to the shopping cart only after opening it.
2. When selecting and adding a product to the basket customer needs to provide the quantity chosen. The product price is calculated by the system based on the current price list.
3. The customer may remove a product with a given price from the cart.
4. The customer can confirm the shopping cart and start the order fulfilment process.
5. The customer may also cancel the shopping cart and reject all selected products.
6. After shopping cart confirmation or cancellation, the product can no longer be added or removed from the cart.

Technically it's modelled as Web API written in [Spring Boot](https://spring.io/projects/spring-boot) and [Java 17](https://www.oracle.com/java/technologies/downloads/). 

## Main assumptions
- explain basics of Event Sourcing, both from the write model ([EventStoreDB](https://developers.eventstore.com/)) and read model part ([PostgreSQL](https://www.postgresql.org/) and [Spring Data JPA](https://spring.io/projects/spring-data-jpa)),
- present that you can join classical approach with Event Sourcing without making a massive revolution,
- [CQRS](https://event-driven.io/en/cqrs_facts_and_myths_explained/) architecture sliced by business features, keeping code that changes together at the same place. Read more in [How to slice the codebase effectively?](https://event-driven.io/en/how_to_slice_the_codebase_effectively/),
- clean, composable (pure) functions for command, events, projections, query handling, minimising the need for marker interfaces. Thanks to that testability and easier maintenance.
- easy to use and self-explanatory fluent API for registering commands and projections with possible fallbacks,
- registering everything into regular DI containers to integrate with other application services.
- pushing the type/signature enforcement on edge, so when plugging to DI.

## Overview

It uses:
- pure data entities, functions and handlers,
- Stores events from the command handler result EventStoreDB,
- Builds read models using [Subscription to `$all`](https://developers.eventstore.com/clients/grpc/subscribing-to-streams/#subscribing-to-all).
- Read models are stored to Postgres relational tables with [Spring Data JPA](https://spring.io/projects/spring-data-jpa).
- App has Swagger and predefined [docker-compose](./docker-compose.yml) to run and play with samples.

## Write Model
- Sample [ShoppingCart](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCart.java) entity and [events](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartEvent.java) represent the business workflow. All events are stored in the same file using [sealed classes](https://blogs.oracle.com/javamagazine/post/java-sealed-classes-fight-ambiguity) to be able to understand flow without jumping from one file to another. It also enables better typing support in pattern matching. `ShoppingCart` also contains [when method](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCart.java#L39) method defining how to apply events to get the entity state. It uses the Java 17 switch syntax for pattern matching.
- Command handlers are defined as static methods in the same file as command definition. It improves ergonomy and reduces context switching as they usually change together. They are pure functions that take command and/or state and create new events based on the business logic. See sample [Adding Product Item to ShoppingCart](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/addingproductitem/AddProductItemToShoppingCart.java#L28). This example also shows that you can inject external services to handlers if needed.
- Code uses [functional interfaces](https://www.theserverside.com/blog/Coffee-Talk-Java-News-Stories-and-Opinions/Get-the-most-from-Java-Function-interface-with-this-example) in many places to introduce composability and lously coupled, testable code.
- Added [EventStoreDB entity store](./src/main/java/io/eventdriven/ecommerce/core/entities/EntityStore.java) to load entity state and store event created by business logic.
- Command and Query handlers are grouped/wrapped into application service, to have the single entry point with all possible operations, see [ShoppingCartService](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartService.java) 
- All command handling has to support [optimistic concurrency](https://event-driven.io/en/optimistic_concurrency_for_pessimistic_times/). Implemented full flow using [If-Match header](./src/main/java/io/eventdriven/ecommerce/api/controller/ShoppingCartsController.java#L87) and returning [ETag](./src/main/java/io/eventdriven/ecommerce/core/http/ETag.java). Read more details in my article [How to use ETag header for optimistic concurrency](https://event-driven.io/pl/how_to_use_etag_header_for_optimistic_concurrency/)

## Read Model
- Read models are rebuilt with eventual consistency using [subscribe to $all stream](https://developers.eventstore.com/clients/grpc/subscribing-to-streams/#subscribing-to-all) EventStoreDB feature,
- Used [Spring Data JPA](https://spring.io/projects/spring-data-jpa) to store projection data into Postgres tables.
- Added sample projection for [Shopping cart details](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/ShoppingCartDetailsProjection.java) and slimmed [Shopping cart short info](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingcarts/ShoppingCartShortInfo.java) as an example of different interpretations of the same events. Shopping cart details also contain a nested collection of product items to show more advanced use case. Added also base [JPAProjection class](./src/main/java/io/eventdriven/ecommerce/core/projections/JPAProjection.java) to reduce needed boilerplate. It supports idempotency by checking the last processed event position against the read model.
- Projections are registered as Spring Boot [event listeners](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/ShoppingCartDetailsProjection.java). Which enables reusing the generic mechanism for publishing events from EventStoreDB,
- Added long-polling example with [If-None-Match](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-None-Match) header handling for getting shopping cart details by id. See details in [ShoppingCartService](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartService.java#L78) and [ShoppingCartDetailsRepository](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/ShoppingCartDetailsRepository.java#L13)
- Used service [EventStoreDBSubscriptionToAll](./src/main/java/io/eventdriven/ecommerce/core/subscriptions/EventStoreDBSubscriptionToAll.java) to handle subscribing to all. It handles checkpointing and resubscribing when the connection is dropped. Added also general [background worker](./src/main/java/io/eventdriven/ecommerce/api/backgroundworkers/EventStoreDBSubscriptionBackgroundWorker.java) to wrap the general [Smart Lifecycle](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/SmartLifecycle.html) handling.
- EventStoreDB subscription forwards events to the Spring Boot [ApplicationEventPublisher](https://reflectoring.io/spring-boot-application-events-explained/) through [EventForwarder](./src/main/java/io/eventdriven/ecommerce/core/events/EventForwarder.java) wrapping them with [EventEnvelope](./src/main/java/io/eventdriven/ecommerce/core/events/EventEnvelope.java) to pass theirs metadata.
- [example query handlers](./src/main/java/io/eventdriven/ecommerce/shoppingcarts/gettingbyid/GetShoppingCartById.java) for reading data t.
- Used checkpointing to EventStoreDB stream with [EventStoreDBSubscriptionCheckpointRepository](./src/main/java/io/eventdriven/ecommerce/core/subscriptions/EventStoreDBSubscriptionCheckpointRepository.java).

### Testing
- Added API integration tests together with a simple wrapper with Specification Pattern to make tests cleaner using Given/When/Then approach. See more in [ApiSpecification](./src/test/java/io/eventdriven/ecommerce/testing/ApiSpecification.java). See example tests in [AddProductItemToShoppingCartTests](./src/test/java/io/eventdriven/ecommerce/api/controller/AddProductItemToShoppingCartTests.java).
- Added basic unit tests for state rebuild in [ShoppingCartTests](./src/test/java/io/eventdriven/ecommerce/shoppingcarts/ShoppingCartTests.java)


## Prerequisites

1. Install git - https://git-scm.com/downloads.
2. Install Java JDK 17 (or later) - https://www.oracle.com/java/technologies/downloads/.
3. Install IntelliJ, Eclipse, VSCode or other preferred IDE.
4. Install docker - https://docs.docker.com/engine/install/.
5. Open project folder.

## Running

1. Run: `docker-compose up`.
2. Wait until all dockers got are downloaded and running.
3. You should automatically get:
    - EventStoreDB UI (for event store): http://localhost:2113/
    - Postgres DB running (for read models)
    - PG Admin - IDE for postgres. Available at: http://localhost:5050.
        - Login: `admin@pgadmin.org`, Password: `admin`
        - To connect to server Use host: `postgres`, user: `postgres`, password: `Password12!`
4. Open, build and run `ECommerceApplication`.
    - Swagger should be available at: http://localhost:8080/swagger-ui/index.html
