
## Step 1

Done. Tracing bridge moved to `implementation`, price calculator made thread-safe, order page shows "being placed" and refreshes every 2 seconds until the status row exists, catalog preview and Playwright use port 3001, HOWTO and README explain the `correlationId` and the two traces. `./gradlew build eventCatalogTest` passed after the last change (14m 26s, 26 catalog tests). Review: clean after one re-review; the one clarity note on `orderPageShowsTheOrderOnceItHasAStatus` is left open.
