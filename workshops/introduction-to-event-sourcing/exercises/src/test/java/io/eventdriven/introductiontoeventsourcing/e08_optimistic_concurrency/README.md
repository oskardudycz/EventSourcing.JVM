# Exercise 09 - Application Logic with Optimistic Concurrency

Having the following shopping cart process:

1. The customer may add a product to the shopping cart only after opening it.
2. When selecting and adding a product to the basket customer needs to provide the quantity chosen. The product price is calculated by the system based on the current price list.
3. The customer may remove a product with a given price from the cart.
4. The customer can confirm the shopping cart and start the order fulfilment process.
5. The customer may also cancel the shopping cart and reject all selected products.
6. After shopping cart confirmation or cancellation, the product can no longer be added or removed from the cart.

![events](./assets/events.jpg)

And business logic and application code implemented in the [previous exercise](../e07_application_logic) update the application code to handle correctly optimistic concurrency. You need to map ETag conditional headers into expected stream revision correctly. Read more:

- [Optimistic concurrency for pessimistic times](https://event-driven.io/en/optimistic_concurrency_for_pessimistic_times/)
- [MDN - HTTP conditional requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Conditional_requests)
- [EventStoreDB Documentation - Handling concurrency](https://developers.eventstore.com/clients/grpc/appending-events.html#handling-concurrency)

This time you'll use EventStoreDB instead of the mocked one. Run `docker compose up` (or for Mac `docker compose -f docker-compose.arm.yml up`) before running test code to have database set up.


There are three variations:

1. Classical, mutable aggregates: [EventStoreDB](./esdb/mutable/), [MongoDB](./mongodb/mutable/),
2. Mixed approach, mutable aggregates, returning events from methods: [EventStoreDB](./esdb/mixed/), [MongoDB](./mongodb/mixed/),
3. Immutable, with functional command handlers composition and entities as data model: [EventStoreDB](./esdb/immutable/), [MongoDB](./mongodb/immutable/),

