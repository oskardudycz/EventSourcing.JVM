# Exercise 05 - Optimistic Concurrency

Having the group checkout modelled in the previous exercises, and defined as:

**Group Checkout** is a quick way to check out all guests of the specific group. Clerk selects set of room stay based on the request from a person from the group and tries to check out all of them.

The main goal for this feature is to speed up existing single checkouts, as they're not efficient for bigger groups and other guests need to stand in the queue.

We still need to support regular checkouts, payments, charges etc. as we do right now.

**Rules:**
1. Guest can checkout only if the financial account balance is settled.
2. Guests cannot checkout if they already checked out.
3. Guests cannot checkout if they are not checked in.

**Notes:**
1. Room Stay means room + length of the stay + main guest
2. Settled means SUM(Charges) - SUM(Payments) === 0


## Goal

**Make the group checkout and guest stay accounts keep every change when handlers run in parallel.**

Each handler loads the state, decides and stores the new state. When two handlers do that for the same entity at the same time, both load the same state, and the one that stores last overwrites the other's change. Duplicates delivered in parallel slip through the idempotency check from the previous exercise the same way: both see that the group checkout doesn't exist yet.

The [in-memory database](./core/Database.java) now versions documents and responds with a delay, like a real one. `get` returns the document with its version, and `store` has an overload taking `expectedVersion`. If the stored version is different, `store` fails with [ConcurrencyException](./core/ConcurrencyException.java). Expected version `0` means the document must not exist yet.

- Start from the idempotent saga of the previous exercise. Choose one path: [DDD Aggregate](./solution1_aggregates) or [Functional Decider](./solution2_immutableentities),
- Store with the version you loaded, so a conflicting change fails instead of overwriting,
- Handle the conflict by loading the current state and deciding again, for a few attempts. Publish only the events of the attempt that succeeded,
- Use the optimistic concurrency tests to guide you and verify if you implemented it correctly. Business process, entity definition and idempotency tests should stay green.

## Tests

The business process tests and the test where the message bus delivers every message twice are in the variant folder. The entity definition, idempotency and optimistic concurrency tests of each module sit next to the module's code.

- DDD Aggregate:
  - [Business process](./solution1_aggregates/BusinessProcessTests.java) and [message bus delivers every message twice](./solution1_aggregates/IdempotencyTests.java),
  - group checkouts: [entity definition](./solution1_aggregates/groupcheckouts/EntityDefinitionTests.java), [idempotency](./solution1_aggregates/groupcheckouts/IdempotencyTests.java) and [optimistic concurrency](./solution1_aggregates/groupcheckouts/OptimisticConcurrencyTests.java),
  - guest stay accounts: [entity definition](./solution1_aggregates/gueststayaccounts/EntityDefinitionTests.java), [idempotency](./solution1_aggregates/gueststayaccounts/IdempotencyTests.java) and [optimistic concurrency](./solution1_aggregates/gueststayaccounts/OptimisticConcurrencyTests.java).
- Functional Decider:
  - [Business process](./solution2_immutableentities/BusinessProcessTests.java) and [message bus delivers every message twice](./solution2_immutableentities/IdempotencyTests.java),
  - group checkouts: [entity definition](./solution2_immutableentities/groupcheckouts/EntityDefinitionTests.java), [idempotency](./solution2_immutableentities/groupcheckouts/IdempotencyTests.java) and [optimistic concurrency](./solution2_immutableentities/groupcheckouts/OptimisticConcurrencyTests.java),
  - guest stay accounts: [entity definition](./solution2_immutableentities/gueststayaccounts/EntityDefinitionTests.java), [idempotency](./solution2_immutableentities/gueststayaccounts/IdempotencyTests.java) and [optimistic concurrency](./solution2_immutableentities/gueststayaccounts/OptimisticConcurrencyTests.java).
