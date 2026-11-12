# To-do

- [ ] Q1 `ESDBEventStore`: implement `subscribe` (recommended) or delete
- [x] Q2 `orders/OrderSaga.java`
- [x] 1. Green build, full transcripts
- [x] 2. One `OrderSaga`
- [ ] 3. Messaging: event store as outbox, one `MessageBus`
- [ ] 4. Payment settlement
- [ ] 5. Timeouts

Notes:
- Step 1: `ShoppingCartTests` needs EventStoreDB: `docker compose up -d eventstore.db` (allowed by Oskar).
- Step 1: `log4j2.xml` status changed from DEBUG to WARN (agreed).
- Step 1: the SLF4J NOP warning means client error logs go nowhere in tests. Known; report only.
- Step 2: javac "unchecked operations" note existed before (generic varargs in CommandBus/EventBus, EventSerializer, EntityStore, test specs). Most should go with step 3.
