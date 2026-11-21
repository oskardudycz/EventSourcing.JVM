# Follow-ups for Oskar (alignment-and-cleanup plan)

Open points that need your decision. The plan run goes on without them.

## F1. A negative client version in `If-Match` (step 2 review, Step-1)

Before step 2, `ShoppingCartFacade` turned a negative client version into "no stream". Now it passes `ExpectedStreamVersion.at(version)`, so `If-Match: W/"-1"` becomes `at(-1)`, and the three stores answer it differently. No ETag the API sends is negative, so only a hand-made request reaches it.

Options:
- A (recommended): `at(n)` accepts only `n >= 0`, and the API answers 412 for a negative version, as for any version that isn't the current one.
- B: keep the old meaning: a negative client version means "no stream".

## F2. Appending zero events (step 2 review, Step-2)

The PostgreSQL function fails on an empty array (`array_length` is NULL), the in-memory store returns `Success(-1, ...)`, and KurrentDB returns its own result. No caller does it: `AggregateStore` and the facades never append nothing.

Options:
- A (recommended): `EventStore.append` rejects zero events with `IllegalArgumentException` on all three stores, and a shared test checks it.
- B: leave it, because no caller reaches it.

## F3. Each module as a separate application (Q14, option B)

After step 3 each module has its own Spring configuration. Running each as its own `@SpringBootApplication` needs a decision on the in-memory profile (it can't cross processes), on the shop pages (they read carts and orders), and on Docker Compose and the run tasks.
