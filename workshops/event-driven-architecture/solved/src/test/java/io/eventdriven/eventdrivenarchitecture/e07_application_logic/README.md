# Exercise 07 - Application Logic

Having the following shopping cart process:

1. The customer may add a product to the shopping cart only after opening it.
2. When selecting and adding a product to the basket customer needs to provide the quantity chosen. The product price is calculated by the system based on the current price list.
3. The customer may remove a product with a given price from the cart.
4. The customer can confirm the shopping cart and start the order fulfilment process.
5. The customer may also cancel the shopping cart and reject all selected products.
6. After shopping cart confirmation or cancellation, the product can no longer be added or removed from the cart.

![events](./assets/events.jpg)

And business logic implemented in the [previous exercise](../e05_business_logic/) write the application code that will _glue_ the API defined in `api.ts` files with the domain code.

This time you'll use EventStoreDB or MongoDB instead of the mocked one. Run `docker compose up` before running test code to have database set up.

There are three variations:

1. Classical, mutable aggregates: [EventStoreDB](./esdb/mutable/), [MongoDB](./mongodb/mutable/),
2. Mixed approach, mutable aggregates, returning events from methods: [EventStoreDB](./esdb/mixed/), [MongoDB](./mongodb/mixed/),
3. Immutable, with functional command handlers composition and entities as data model: [EventStoreDB](./esdb/immutable/), [MongoDB](./mongodb/immutable/),

Select your preferred approach (or both) to solve this use case.

Read also my articles on business logic composition:

- [How to effectively compose your business logic](https://event-driven.io/en/how_to_effectively_compose_your_business_logic//?utm_source=eventsourcing_jvm?utm_campaign=workshop)
- [Slim your aggregates with Event Sourcing!](https://event-driven.io/en/slim_your_entities_with_event_sourcing/?utm_source=eventsourcing_jvm?utm_campaign=workshop)
