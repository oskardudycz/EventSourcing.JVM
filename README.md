[![Twitter Follow](https://img.shields.io/twitter/follow/oskar_at_net?style=social)](https://twitter.com/oskar_at_net) [![Github Sponsors](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&link=https://github.com/sponsors/oskardudycz/)](https://github.com/sponsors/oskardudycz/)  ![Github Actions](https://github.com/oskardudycz/EventSourcing.JVM/actions/workflows/samples_event-sourcing-esdb-simple.yml/badge.svg?branch=main) [![blog](https://img.shields.io/badge/blog-event--driven.io-brightgreen)](https://event-driven.io/?utm_source=event_sourcing_jvm) [![blog](https://img.shields.io/badge/%F0%9F%9A%80-Architecture%20Weekly-important)](https://www.architecture-weekly.com/?utm_source=event_sourcing_jvm) 

# EventSourcing.JVM

- [EventSourcing.JVM](#eventsourcingjvm)
  - [Event Sourcing](#event-sourcing)
    - [What is Event Sourcing?](#what-is-event-sourcing)
    - [What is Event?](#what-is-event)
    - [What is Stream?](#what-is-stream)
    - [Event representation](#event-representation)
    - [Retrieving the current state from events](#retrieving-the-current-state-from-events)
    - [Event Store](#event-store)
  - [Videos](#videos)
    - [Practical Event Sourcing with Marten](#practical-event-sourcing-with-marten)
    - [The Light and The Dark Side of the Event-Driven Design](#the-light-and-the-dark-side-of-the-event-driven-design)
    - [Conversation with Yves Lorphelin about CQRS](#conversation-with-yves-lorphelin-about-cqrs)
  - [Support](#support)
  - [Samples](#samples)
    - [Event Sourcing with Spring Boot and EventStoreDB](#event-sourcing-with-spring-boot-and-eventstoredb)
      - [Overview](#overview)
      - [Main assumptions](#main-assumptions)
      - [Prerequisites](#prerequisites)
      - [Tools used](#tools-used)
  - [Articles](#articles)
  
## Event Sourcing

### What is Event Sourcing?

Event Sourcing is a design pattern in which results of business operations are stored as a series of events. 

It is an alternative way to persist data. In contrast with state-oriented persistence that only keeps the latest version of the entity state, Event Sourcing stores each state change as a separate event.

Thanks for that, no business data is lost. Each operation results in the event stored in the databse. That enables extended auditing and diagnostics capabilities (both technically and business-wise). What's more, as events contains the business context, it allows wide business analysis and reporting.

In this repository I'm showing different aspects, patterns around Event Sourcing. From the basic to advanced practices.

Read more in my article:
-   📝 [How using events helps in a teams' autonomy](https://event-driven.io/en/how_using_events_help_in_teams_autonomy/?utm_source=event_sourcing_jvm)
-   📝 [When not to use Event Sourcing?](https://event-driven.io/en/when_not_to_use_event_sourcing/?utm_source=event_sourcing_jvm)

### What is Event?

Events, represent facts in the past. They carry information about something accomplished. It should be named in the past tense, e.g. _"user added"_, _"order confirmed"_. Events are not directed to a specific recipient - they're broadcasted information. It's like telling a story at a party. We hope that someone listens to us, but we may quickly realise that no one is paying attention.

Events:
- are immutable: _"What has been seen, cannot be unseen"_.
- can be ignored but cannot be retracted (as you cannot change the past).
- can be interpreted differently. The basketball match result is a fact. Winning team fans will interpret it positively. Losing team fans - not so much.

Read more in my articles:
-   📝 [What's the difference between a command and an event?](https://event-driven.io/en/whats_the_difference_between_event_and_command/?utm_source=event_sourcing_jvm)
-   📝 [Events should be as small as possible, right?](https://event-driven.io/en/whats_the_difference_between_event_and_command/?utm_source=event_sourcing_jvm)

### What is Stream?

Events are logically grouped into streams. In Event Sourcing, streams are the representation of the entities. All the entity state mutations ends up as the persisted events. Entity state is retrieved by reading all the stream events and applying them one by one in the order of appearance.

A stream should have a unique identifier representing the specific object. Each event has its own unique position within a stream. This position is usually represented by a numeric, incremental value. This number can be used to define the order of the events while retrieving the state. It can be also used to detect concurrency issues. 

### Event representation

Technically events are messages. 

They may be represented, e.g. in JSON, Binary, XML format. Besides the data, they usually contain:
- **id**: unique event identifier.
- **type**: name of the event, e.g. _"invoice issued"_.
- **stream id**: object id for which event was registered (e.g. invoice id).
- **stream position** (also named _version_, _order of occurrence_, etc.): the number used to decide the order of the event's occurrence for the specific object (stream).
- **timestamp**: representing a time at which the event happened.
- other metadata like `correlation id`, `causation id`, etc.

Sample event JSON can look like:

```json
{
  "id": "e44f813c-1a2f-4747-aed5-086805c6450e",
  "type": "invoice-issued",
  "streamId": "INV/2021/11/01",
  "streamPosition": 1,
  "timestamp": "2021-11-01T00:05:32.000Z",

  "data":
  {
    "issuedTo": {
      "name": "Oscar the Grouch",
      "address": "123 Sesame Street",
    },
    "amount": 34.12,
    "number": "INV/2021/11/01",
    "issuedAt": "2021-11-01T00:05:32.000Z"
  },

  "metadata": 
  {
    "correlationId": "1fecc92e-3197-4191-b929-bd306e1110a4",
    "causationId": "c3cf07e8-9f2f-4c2d-a8e9-f8a612b4a7f1"
  }
}
```

### Retrieving the current state from events

In Event Sourcing, the state is stored in events. Events are logically grouped into streams. Streams can be thought of as the entities' representation. Traditionally (e.g. in relational or document approach), each entity is stored as a separate record.

| Id       | IssuerName       | IssuerAddress     | Amount | Number         | IssuedAt   |
| -------- | ---------------- | ----------------- | ------ | -------------- | ---------- |
| e44f813c | Oscar the Grouch | 123 Sesame Street | 34.12  | INV/2021/11/01 | 2021-11-01 |

 In Event Sourcing, the entity is stored as the series of events that happened for this specific object, e.g. `InvoiceInitiated`, `InvoiceIssued`, `InvoiceSent`. 

```json          
[
    {
        "id": "e44f813c-1a2f-4747-aed5-086805c6450e",
        "type": "invoice-initiated",
        "streamId": "INV/2021/11/01",
        "streamPosition": 1,
        "timestamp": "2021-11-01T00:05:32.000Z",

        "data":
        {
            "issuedTo": {
                "name": "Oscar the Grouch",
                "address": "123 Sesame Street",
            },
            "amount": 34.12,
            "number": "INV/2021/11/01",
            "initiatedAt": "2021-11-01T00:05:32.000Z"
        }
    },        
    {
        "id": "5421d67d-d0fe-4c4c-b232-ff284810fb59",
        "type": "invoice-issued",
        "streamId": "INV/2021/11/01",
        "streamPosition": 2,
        "timestamp": "2021-11-01T00:11:32.000Z",

        "data":
        {
            "issuedTo": "Cookie Monster",
            "issuedAt": "2021-11-01T00:11:32.000Z"
        }
    },        
    {
        "id": "637cfe0f-ed38-4595-8b17-2534cc706abf",
        "type": "invoice-sent",
        "streamId": "INV/2021/11/01",
        "streamPosition": 3,
        "timestamp": "2021-11-01T00:12:01.000Z",

        "data":
        {
            "sentVia": "email",
            "sentAt": "2021-11-01T00:12:01.000Z"
        }
    }
]
```

All of those events shares the stream id (`"streamId": "INV/2021/11/01"`), and have incremented stream position.
We can get to conclusion that in Event Sourcing entity is represented by stream, so sequence of event correlated by the stream id ordered by stream position.

To get the current state of entity we need to perform the stream aggregation process. We're translating the set of events into a single entity. This can be done with the following the steps:
1. Read all events for the specific stream.
2. Order them ascending in the order of appearance (by the event's stream position).
3. Construct the empty object of the entity type (e.g. with default constructor).
4. Apply each event on the entity.

This process is called also _stream aggregation_ or _state rehydration_.

Read more in my article:
-   📝 [Why Partial<Type> is an extremely useful TypeScript feature?](https://event-driven.io/en/partial_typescript/?utm_source=event_sourcing_jvm)
-   📝 [How to get the current entity state from events?](https://event-driven.io/en/how_to_get_the_current_entity_state_in_event_sourcing/?utm_source=event_sourcing_jvm)

### Event Store

Event Sourcing is not related to any type of storage implementation. As long as it fulfils the assumptions, it can be implemented having any backing database (relational, document, etc.). The state has to be represented by the append-only log of events. The events are stored in chronological order, and new events are appended to the previous event. Event Stores are the databases' category explicitly designed for such purpose. 

In the further samples, I'll use [EventStoreDB](https://developers.eventstore.com/). It's the battle-tested OSS database created and maintained by the Event Sourcing authorities. It supports many dev environments via gRPC clients, including JVM.

Read more in my article:
-   📝 [What if I told you that Relational Databases are in fact Event Stores?](https://event-driven.io/en/relational_databases_are_event_stores/=event_sourcing_jvm)

## Videos

### Practical Event Sourcing with Marten

### The Light and The Dark Side of the Event-Driven Design

<a href="https://www.youtube.com/watch?v=0pYmuk0-N_4" target="_blank"><img src="https://img.youtube.com/vi/0pYmuk0-N_4/0.jpg" alt="The Light and The Dark Side of the Event-Driven Design" width="320" height="240" border="10" /></a>

### Conversation with [Yves Lorphelin](https://github.com/ylorph/) about CQRS

<a href="https://www.youtube.com/watch?v=D-3N2vQ7ADE" target="_blank"><img src="https://img.youtube.com/vi/D-3N2vQ7ADE/0.jpg" alt="Event Store Conversations: Yves Lorphelin talks to Oskar Dudycz about CQRS (EN)" width="320" height="240" border="10" /></a>

## Support

Feel free to [create an issue](https://github.com/oskardudycz/EventSourcing.JVM/issues/new) if you have any questions or request for more explanation or samples. I also take **Pull Requests**!

💖 If this repository helped you - I'd be more than happy if you **join** the group of **my official supporters** at:

👉 [Github Sponsors](https://github.com/sponsors/oskardudycz) 

⭐ Star on GitHub or sharing with your friends will also help!

## Samples

See also fully working, real-world samples of Event Sourcing and CQRS applications in [Samples folder](./samples/event-sourcing-esdb-simple).

Samples are using CQRS architecture. They're sliced based on the business modules and operations. Read more about the assumptions in ["How to slice the codebase effectively?"](https://event-driven.io/en/how_to_slice_the_codebase_effectively/?utm_source=event_sourcing_jvm).

### [Event Sourcing with Spring Boot and EventStoreDB](./samples/event-sourcing-esdb-simple)

#### Overview

Sample is showing basic Event Sourcing flow. It uses [EventStoreDB](https://developers.eventstore.com/) for event storage and [Spring Data JPA](https://spring.io/projects/spring-data-jpa) backed with [PostgreSQL](https://www.postgresql.org/) for read models. 

The presented use case is Shopping Cart flow:
1. The customer may add a product to the shopping cart only after opening it.
2. When selecting and adding a product to the basket customer needs to provide the quantity chosen. The product price is calculated by the system based on the current price list.
3. The customer may remove a product with a given price from the cart.
4. The customer can confirm the shopping cart and start the order fulfilment process.
5. The customer may also cancel the shopping cart and reject all selected products.
6. After shopping cart confirmation or cancellation, the product can no longer be added or removed from the cart.

Technically it's modelled as Web API written in [Spring Boot](https://spring.io/projects/spring-boot) and [Java 17](https://www.oracle.com/java/technologies/downloads/). 

#### Main assumptions
- explain basics of Event Sourcing, both from the write model ([EventStoreDB](https://developers.eventstore.com/)) and read model part ([PostgreSQL](https://www.postgresql.org/) and [Spring Data JPA](https://spring.io/projects/spring-data-jpa)),
- present that you can join classical approach with Event Sourcing without making a massive revolution,
- [CQRS](https://event-driven.io/en/cqrs_facts_and_myths_explained/) architecture sliced by business features, keeping code that changes together at the same place. Read more in [How to slice the codebase effectively?](https://event-driven.io/en/how_to_slice_the_codebase_effectively/),
- clean, composable (pure) functions for command, events, projections, query handling, minimising the need for marker interfaces. Thanks to that testability and easier maintenance.
- easy to use and self-explanatory fluent API for registering commands and projections with possible fallbacks,
- registering everything into regular DI containers to integrate with other application services.
- pushing the type/signature enforcement on edge, so when plugging to DI.

#### Prerequisites

For running the Event Store examples you need to have:

1. Java JDK 17 (or later) installed - https://www.oracle.com/java/technologies/downloads/.
2. Installed IntelliJ, Eclipse, VSCode or other preferred IDE.
3. [Docker](https://store.docker.com/search?type=edition&offering=community) installed.

#### Tools used

1. [EventStoreDB](https://eventstore.com) - Event Store 
2. [PostgreSQL](https://www.postgresql.org/) - Read Models
3. [Spring Boot](https://spring.io/projects/spring-boot) - Web Application framework

## Articles

Read also more on the **Event Sourcing** and **CQRS** topics in my [blog](https://event-driven.io/?utm_source=event_sourcing_jvm) posts:
-   📝 [What's the difference between a command and an event?](https://event-driven.io/en/whats_the_difference_between_event_and_command/?utm_source=event_sourcing_jvm)
-   📝 [Event Streaming is not Event Sourcing!](https://event-driven.io/en/event_streaming_is_not_event_sourcing/?utm_source=event_sourcing_jvm)
-   📝 [Events should be as small as possible, right?](https://event-driven.io/en/events_should_be_as_small_as_possible/?utm_source=event_sourcing_jvm)
-   📝 [How to get the current entity state from events?](https://event-driven.io/en/how_to_get_the_current_entity_state_in_event_sourcing/?utm_source=event_sourcing_jvm)
-   📝 [Anti-patterns in event modelling - Property Sourcing](https://event-driven.io/en/property-sourcing/?utm_source=event_sourcing_jvm)
-   📝 [Anti-patterns in event modelling - State Obsession](https://event-driven.io/en/state-obsession/?utm_source=event_sourcing_jvm)
-   📝 [Why a bank account is not the best example of Event Sourcing?](https://event-driven.io/en/bank_account_event_sourcing/?utm_source=event_sourcing_jvm)
-   📝 [When not to use Event Sourcing?](https://event-driven.io/en/when_not_to_use_event_sourcing/?utm_source=event_sourcing_jvm)
-   📝 [CQRS facts and myths explained](https://event-driven.io/en/cqrs_facts_and_myths_explained/?utm_source=event_sourcing_jvm)
-   📝 [How to slice the codebase effectively?](https://event-driven.io/en/how_to_slice_the_codebase_effectively/?utm_source=event_sourcing_jvm)
-   📝 [Can command return a value?](https://event-driven.io/en/can_command_return_a_value/?utm_source=event_sourcing_jvm)
-   📝 [How to register all CQRS handlers by convention](https://event-driven.io/en/how_to_register_all_mediatr_handlers_by_convention/?utm_source=event_sourcing_jvm)
-   📝 [How to use ETag header for optimistic concurrency](https://event-driven.io/en/how_to_use_etag_header_for_optimistic_concurrency/?utm_source=event_sourcing_jvm)
-   📝 [Dealing with Eventual Consistency and Idempotency in MongoDB projections](https://event-driven.io/en/dealing_with_eventual_consistency_and_idempotency_in_mongodb_projections/?utm_source=event_sourcing_jvm)
-   📝 [Long-polling, how to make our async API synchronous](https://event-driven.io/en/long_polling_and_eventual_consistency/?utm_source=event_sourcing_jvm)
-   📝 [A simple trick for idempotency handling in the Elastic Search read model](https://event-driven.io/en/simple_trick_for_idempotency_handling_in_elastic_search_readm_model/?utm_source=event_sourcing_jvm)
-   📝 [How to do snapshots in Marten?](https://event-driven.io/en/how_to_do_snapshots_in_Marten/?utm_source=event_sourcing_jvm)
-   📝 [Integrating Marten with other systems](https://event-driven.io/en/integrating_Marten/?utm_source=event_sourcing_jvm)
-   📝 [How to (not) do the events versioning?](https://event-driven.io/en/how_to_do_event_versioning/?utm_source=event_sourcing_jvm)
-   📝 [Simple patterns for events schema versioning](https://event-driven.io/en/simple_events_versioning_patterns/?utm_source=event_sourcing_jvm)
-   📝 [How to create projections of events for nested object structures?](https://event-driven.io/en/how_to_create_projections_of_events_for_nested_object_structures/?utm_source=event_sourcing_jvm)
-   📝 [How to scale projections in the event-driven systems?](https://event-driven.io/en/how_to_scale_projections_in_the_event_driven_systems/?utm_source=event_sourcing_jvm)
-   📝 [Immutable Value Objects are simpler and more useful than you think!](https://event-driven.io/en/immutable_value_objects/?utm_source=event_sourcing_jvm)
-   📝 [Notes about C# records and Nullable Reference Types](https://event-driven.io/en/notes_about_csharp_records_and_nullable_reference_types/?utm_source=event_sourcing_jvm)
-   📝 [Using strongly-typed identifiers with Marten](https://event-driven.io/en/using_strongly_typed_ids_with_marten/?utm_source=event_sourcing_jvm)
-   📝 [How using events helps in a teams' autonomy](https://event-driven.io/en/how_using_events_help_in_teams_autonomy/?utm_source=event_sourcing_jvm)
-   📝 [What texting your Ex has to do with Event-Driven Design?](https://event-driven.io/en/what_texting_ex_has_to_do_with_event_driven_design/?utm_source=event_sourcing_jvm)
-   📝 [What if I told you that Relational Databases are in fact Event Stores?](https://event-driven.io/en/relational_databases_are_event_stores/?utm_source=event_sourcing_jvm)
-   📝 [Optimistic concurrency for pessimistic times](https://event-driven.io/en/optimistic_concurrency_for_pessimistic_times/?utm_source=event_sourcing_jvm)
-   📝 [Outbox, Inbox patterns and delivery guarantees explained](https://event-driven.io/en/outbox_inbox_patterns_and_delivery_guarantees_explained/?utm_source=event_sourcing_jvm)
-   📝 [Saga and Process Manager - distributed processes in practice](https://event-driven.io/en/saga_process_manager_distributed_transactions/?utm_source=event_sourcing_jvm)


**EventSourcing.JVM** is Copyright &copy; 2022 [Oskar Dudycz](http://event-driven.io) and other contributors under the [MIT license](LICENSE).