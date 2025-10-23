# Exercise 03 - Business Processes

Having the group checkout modelled in the previous exercise, and defined as:

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

**Based on the business logic solution from the previous exercise. Model the process and business logic of the Group Checkout.** 
- Choose one path [DDD Aggregate](./solution1_aggregates) or [Functional Decider](./solution2_immutableentities)
- Fill application logic in GroupCheckoutFacade, using the [in-memory database](./core/Database.java) and [in-memory event bus](./core/EventBus.java) to store changes and publish events as the outcome of business logic,
- Configure commands handling from command bus and event handling in event bus in respective Config files (think about them as setup called once upon application startup),
- You can use any preferred style of implementing process coordination,
- Use BusinessProcessTests to guide you and verify if you implemented it correctly.
