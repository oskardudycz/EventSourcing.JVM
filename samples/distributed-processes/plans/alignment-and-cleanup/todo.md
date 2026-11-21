# Todo: Alignment and cleanup of samples/distributed-processes

Plan: `plan.md`. Spec: `spec.md`.

## Steps

- [x] 1. Quick fixes: tracing in the running app, the price calculator, the shop after checkout, the catalog port (1, 10, 11, 12)
- [x] 2. The event store contract and the PostgreSQL append function (2, 3)
- [x] 3. Modules configured on their own, and tests that wait on catch-up instead of time (16, 17.1-17.3)
- [x] 4. One type mapper and serializer, and store subscriptions by type name (4, 5, 17.4)
- [ ] 5. The shop on command handlers and immutable entities (6, 8, 9)
- [ ] 6. Broker alignment: dead-letter tags, RabbitMQ retry, in-memory step spans (13, 15)
- [ ] 7. Names, dead code, test helpers, settings and docs (no criteria)
