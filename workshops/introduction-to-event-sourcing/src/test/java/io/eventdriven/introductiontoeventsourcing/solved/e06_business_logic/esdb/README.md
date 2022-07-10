# Exercise 06 - Business Logic with EventStoreDB

Having the following shopping cart process:
1. The customer may add a product to the shopping cart only after opening it.
2. When selecting and adding a product to the basket customer needs to provide the quantity chosen. The product price is calculated by the system based on the current price list.
3. The customer may remove a product with a given price from the cart.
4. The customer can confirm the shopping cart and start the order fulfilment process.
5. The customer may also cancel the shopping cart and reject all selected products.
6. After shopping cart confirmation or cancellation, the product can no longer be added or removed from the cart.

Write the code that fulfils this logic. Remember that in Event Sourcing each business operation has to result with a new business fact (so event). Use events and entities defined in previous exercises.

![events](./assets/events.jpg)

There are two variations:
1. Immutable, with functional command handlers composition and entities as anemic data model: [immutable/BusinessLogicTests.java](./immutable/BusinessLogicTests.java).
2. Classical, mutable aggregates (rich domain model): [Mutable/BusinessLogicTests.java](./mutable/BusinessLogicTests.java).
3. Mixed approach, mutable aggregates (rich domain model), returning events from methods, using immutable DTOs: [Mixed/BusinessLogicTests.java](./mixed/BusinessLogicTests.java).

Select your preferred approach (or all) to solve this use case using EventStoreDB. Fill appropriate `EntityStore` implementation.

_**Note**: If needed update entities, events or test setup structure_

## Solution

1. Immutable, with functional command handlers composition and entities as anemic data model: [Immutable/BusinessLogic.java](./immutable/BusinessLogic.java). Read also more about decider pattern [Jérémie Chassaing - Functional Event Sourcing Decider](https://thinkbeforecoding.com/post/2021/12/17/functional-event-sourcing-decider)
2. Classical, mutable aggregates (rich domain model): [Mutable/Solution1/BusinessLogic.java](./mutable/BusinessLogic.java).
3. Mixed approach, mutable aggregates (rich domain model), returning events from methods, using immutable DTOs: [Mutable/Solution2/BusinessLogic.java](./mixed/BusinessLogic.java).

