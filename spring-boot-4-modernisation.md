# Spring Boot 4 modernisation: research and suggestions

Status: proposal for Oskar's approval. Nothing below is implemented.

Sources:

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)

Baseline: all JVM projects now build on Spring Boot 4.1.2, Gradle 9.2.1, Jackson 3 (except events-versioning) and JUnit 6, with tests on Testcontainers. The migration made the code compile and pass; the items below make it idiomatic for Boot 4 / Framework 7.

## What the guides recommend that applies here

| Topic | Boot 4 / Framework 7 guidance | Where it applies in this repo |
| --- | --- | --- |
| Modular starters | `spring-boot-starter-web` is deprecated; use `spring-boot-starter-webmvc`. Each technology has its own test starter (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, ...), which brings `spring-boot-starter-test` transitively. | `spring-boot-starter-web` in simple, aggregates, intro exercises/solved, EDA exercises/solved. Generic `spring-boot-starter-test` in 8 projects. |
| Retry | Spring Retry has no dependency management any more. Framework 7 has `RetryTemplate`/`RetryPolicy` in `org.springframework.core.retry` and `@Retryable` + `@ConcurrencyLimit` enabled by `@EnableResilientMethods`. | simple and aggregates use `org.springframework.retry` (`KurrentDBSubscriptionToAll`, `GetShoppingCartById` long polling) with a pinned `spring-retry:2.0.13`. The intro workshop declares `spring-retry` but never uses it. distributed-processes and uniqueness have their own `RetryPolicy`/`NulloRetryPolicy`. |
| HTTP tests | `RestTemplate` is deprecated in the Framework 7 docs and will be `@Deprecated` in 7.1. `TestRestTemplate` now needs `spring-boot-resttestclient` + `spring-boot-restclient` + `@AutoConfigureTestRestTemplate`. The new `RestTestClient` (`@AutoConfigureRestTestClient`) is the non-reactive counterpart of `WebTestClient`. | `ApiSpecification` in simple, aggregates, intro e07/e08 (exercises and solved, about 40 files use `TestRestTemplate`). |
| Jackson | Jackson 3 is the default. Boot auto-configures a `JsonMapper`; `spring.jackson.use-jackson2-defaults=true` restores Boot 3 defaults. Jackson 2 support is deprecated and goes away in Framework 7.2. | simple and aggregates set `FAIL_ON_NULL_FOR_PRIMITIVES=false` on a custom mapper bean. events-versioning stays on Jackson 2 because strictland needs an `ObjectMapper` from Jackson 2. Explicit `tools.jackson.core:jackson-databind`/`jackson-core` dependencies next to Boot starters. |
| Null safety | JSpecify replaces Spring's `@NonNullApi`/`@Nullable` and JSR 305. | Done in the migration (38 `package-info.java` now `@NullMarked`). No action. |
| Configuration | `@ConfigurationProperties` records are the idiomatic way to bind groups of settings; `@Value` for single values is still fine. | `@Value("${kurrentdb.connectionstring}")` / `mongodb.connectionstring` in 26 config methods; EDA uses `@Value` for Kafka/RabbitMQ names and already has 2 `@ConfigurationProperties`. |
| Testing | `@MockBean`/`@SpyBean` removed. `@SpringBootTest` no longer provides MockMvc/TestRestTemplate without `@AutoConfigure...`. | No `@MockBean` usages. Field `@Autowired` in test base classes (6 places in samples/intro), 14 in EDA solved. |
| Virtual threads | `spring.threads.virtual.enabled=true`; JDK `HttpClient`-based clients then use virtual threads. | Not used. Apps are Java 21/22. |
| Observability | `spring-boot-starter-opentelemetry` exports traces and metrics over OTLP. | EDA solved has its own OTLP setup behind `-Pobservability`. |

## Suggestions (for approval)

Ordered by value for a teaching repo vs. effort.

1. **Use the Boot 4 starter names.** Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc`, and generic `spring-boot-starter-test` with the technology test starters (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test` where JPA slices are used). Small, mechanical, removes deprecated names. Affects simple, aggregates, intro, EDA.
2. **Replace Spring Retry with Framework 7 retry.** In simple and aggregates, move `KurrentDBSubscriptionToAll` and the long polling in `GetShoppingCartById` to `org.springframework.core.retry.RetryTemplate` + `RetryPolicy`, and drop the `spring-retry` dependency. Remove the unused `spring-retry` from the intro workshop. Removes an unmanaged dependency.
3. **Switch API tests from `TestRestTemplate` to `RestTestClient`.** It's the replacement the guide points to, and `RestTemplate` is on its way out. Affects `ApiSpecification` and its Given/When/Then helpers in simple, aggregates and intro e07/e08 (exercises and solved). Larger change; test semantics stay the same.
4. **Replace the custom mapper flag with Boot's setting.** In simple and aggregates, replace `FAIL_ON_NULL_FOR_PRIMITIVES=false` on a hand-built mapper with either `spring.jackson.use-jackson2-defaults=true` or a `JsonMapperBuilderCustomizer`, and rely on the starter instead of listing `jackson-databind`/`jackson-core` explicitly.
5. **Bind connection settings with a `@ConfigurationProperties` record** (e.g. `KurrentDBProperties(String connectionString)`) instead of repeating `@Value("${kurrentdb.connectionstring}")` in many config classes. Mainly the intro workshop (12+12 places).
6. **Constructor injection in test base classes** instead of field `@Autowired` where JUnit allows it (`@Autowired` on the constructor of a `@SpringBootTest` class). Optional, style only.
7. **Enable virtual threads** in the Spring apps (`spring.threads.virtual.enabled=true`). One line per app; shows a modern default. Optional.
8. **Leave as is:** custom `RetryPolicy` in distributed-processes and uniqueness (they model handler acknowledgement, not plain retry), springdoc (3.2.0 supports Boot 4), log4j2 setup (follows f3d22f7), events-versioning on Jackson 2 (blocked by strictland).

## Questions

1. Which of suggestions 1-7 should I implement? My recommendation: 1, 2 and 4 now (small, remove deprecated or unmanaged pieces); 3 as a separate step (largest diff, touches workshop exercises); 5-7 only if you want them.
2. For suggestion 3, should the workshop exercises also switch to `RestTestClient`, or only the samples and solved code? Exercises and solved share the same `ApiSpecification` shape today.
3. For suggestion 4, do you prefer the global `spring.jackson.use-jackson2-defaults=true`, or keeping the one explicit feature in a `JsonMapperBuilderCustomizer` (shows readers what changed)?
4. For suggestion 2, should distributed-processes and uniqueness also move their retry loops onto Framework 7's `RetryTemplate`, or keep their own `RetryPolicy` abstraction?
5. Should strictland get Jackson 3 support, so events-versioning can drop Jackson 2 before Framework 7.2 removes it?

## Decisions (Oskar, 2026-11-14)

- Implement suggestions 1-4. For 4, keep the explicit `FAIL_ON_NULL_FOR_PRIMITIVES=false` on `EventSerializer.mapper`: the app builds its own `JsonMapper` (also exposed as Spring's bean), so `spring.jackson.use-jackson2-defaults` and `JsonMapperBuilderCustomizer` would not affect it. Only drop the explicit `jackson-databind`/`jackson-core` dependencies if the starter brings them.
- Implement 5 (configuration properties records). Skip virtual threads for now: toolchains are Java 21/22, where `synchronized` pinning still applies (fixed in Java 24, JEP 491); revisit with Java 25.
- `RestTestClient` also in the workshop exercises.
- distributed-processes and uniqueness: replace the custom `RetryPolicy` with Spring Framework 7 `RetryTemplate`.
- events-versioning: write a Jackson 3 `MessageSerializer` for Strictland in the sample's tests and move the sample to Jackson 3; discuss upstream Strictland support afterwards.
- Mockito: load it as a Java agent in the `test` task of the Spring projects.
- classgraph stays on 5.0.2. EDA `spring.datasource.*` and JDBC dependencies stay (used soon).
- Workshops: tests use Testcontainers by default, with an opt-in switch (env var / Gradle property) to use the running `docker compose` so attendees can inspect data in the UIs. Samples: Testcontainers only; docker-compose.yml stays for running the apps.
- KurrentDBContainer: pick the ARM64 image on ARM, like Emmett's `eventStoreDBContainer.ts` (`process.arch === 'arm64'`), using `os.arch`.
- Intro workshop manual runs: keep the localhost values in `application.properties` (tests override them) and document `./gradlew bootTestRun` / IDE runs against `docker compose up`.
