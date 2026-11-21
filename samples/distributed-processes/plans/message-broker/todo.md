# Todo: Real message brokers, event stores and observability for the e-commerce sample

Plan: `plan.md`. Spec: `spec.md`.

## Steps

- [x] 1. Explicit routes and declared channels on the in-memory bus (15)
- [x] 2. The settings and the `in-memory` profile (5)
- [x] 3. Projections on JPA (5, 29)
- [x] 4. The five values on events and messages, in memory (28, 35)
- [x] 5. One named subscription per subscriber (no criteria)
- [x] 6. KurrentDB as the event store, with checkpoints (24, 25, 26, 27)
- [x] 7. PostgreSQL as the event store (24, 25, 26, 27, 28)
- [x] 8. The bus on Kafka (6, 7, 8, 9, 10, 12, 14, 15, 22, 35)
- [x] 9. Failures and versioning on Kafka (11, 16, 17, 18, 19, 23)
- [x] 10. The bus on RabbitMQ (6, 7, 8, 9, 13, 15, 22, 35)
- [x] 11. Failures and versioning on RabbitMQ (16, 17, 18, 19, 20, 21, 23)
- [x] 11a. A stable event catalog check (no criteria)
- [x] 12. One purchase on Kafka in one trace (30, 31, 32, 33, 34, 36)
- [x] 13. One purchase on RabbitMQ in one trace (37)
- [x] 14. Traces, logs and metrics in Grafana (38, 39, 40, 41, 42)
- [x] 15. Real infrastructure by default (1, 2, 3, 4, 45)
- [x] 16. AsyncAPI servers and documentation (43, 44)

## Open items

- [x] P1. "Pay now" and "Retry payment" (answered Q33: plain facade calls, new flow)
- [x] P2. The Docker Compose Kafka in a dev container (answered Q32: document the limit, step 16)
- [x] P3. The KurrentDB client's spans (answered Q34: own streams.append Observation, step 14)
