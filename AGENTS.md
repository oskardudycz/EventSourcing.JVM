# EventSourcing.JVM

Tutorials, workshops and samples about Event Sourcing and event-driven design on the JVM. See `README.md` for what each part teaches.

## Project structure

The repository groups code by purpose:

```
samples/
├── distributed-processes/                  Distributed process with Event Sourcing
├── event-sourcing-kurrentdb-aggregates/    Event Sourcing with Spring Boot and KurrentDB using Aggregate pattern
├── event-sourcing-kurrentdb-simple/        Event Sourcing with Spring Boot and KurrentDB
├── events-versioning/                      Event Schema Versioning
├── stream-metadata/
├── talk/
└── uniqueness/                             Handling Uniqueness in Event Sourcing
workshops/
├── build-your-own-event-store/{exercises,solved}/
├── event-driven-architecture/{exercises,solved}/
└── introduction-to-event-sourcing/{exercises,solved}/
```

There is no root build. Each sample and each workshop folder is a separate Gradle build with its own wrapper, so you build and test one folder at a time, from inside that folder. Each one also has its own CI workflow in `.github/workflows`, which runs only when that folder changes.

The `exercises` folders hold the workshop tasks, and the `solved` folders hold their solutions.

The repository root and each of the 13 folders have their own copies of `.devcontainer/devcontainer.json`, `.vscode/`, `.editorconfig` and `.idea/externalDependencies.xml`. The copies differ only in folder names and paths.

## Build environment

The builds compile for Java 22 (`options.release = 22` in `build.gradle`). Gradle builds and tests with the JDK it runs on when that JDK is 22 or newer. With an older JDK, `build.gradle` asks for a Java 22 toolchain, and Gradle uses an installed JDK 22 or downloads it. CI checks JDK 22, 25 and 27. A missing JDK is not a reason to skip a build.

The tests need Docker. They start KurrentDB, PostgreSQL and MongoDB with Testcontainers, so they fail if Docker isn't running. If a KurrentDB from Docker Compose is running (`docker compose --profile kurrentdb up` in the repository root), the tests that get their connection string from `TestDatabases` use it instead of starting a container.

In a dev container, the repository root is mounted at `/workspaces/EventSourcing.JVM`, and Docker is the host's Docker, reached through its socket. There, don't run the Kafka tests of `workshops/event-driven-architecture/solved` while the Docker Compose Kafka runs: the tests find it and fail, because it advertises `localhost:9092`, which inside the container is the container itself. Without it, the tests start their own Kafka.

### Everyday commands

From a sample or workshop folder:

```shell
./gradlew build         # compile and run all tests
./gradlew testClasses   # compile only - the fast inner loop
./gradlew test          # run the tests
./gradlew spotlessApply # format the code
```

### Additional commands

Run from a sample or workshop folder:

```shell
./gradlew test --tests 'io.eventdriven.uniqueness.shoppingcarts.ShoppingCartTests' # run one test class
```

## Code style

Formatting is handled for you by [Spotless](https://github.com/diffplug/spotless), with each folder's Gradle build as the single source of truth:

- **Java** - [palantir-java-format](https://github.com/palantir/palantir-java-format)

Don't format code by hand or with another tool. The stop hook runs `./gradlew spotlessApply` in each build you edited (see "Verification"), and the pre-commit hook in `.githooks` and the editor settings use the same config. `./gradlew spotlessCheck` shows any drift, but it is not part of `build` (`enforceCheck false`), so formatting never fails a build or CI. The pre-commit hook stops a commit when the formatter changes a staged file.

palantir-java-format can't format an unnamed variable (`_`) inside a record pattern with `var`, for example `case ProductItemAdded(_, var productItem)` ([palantir-java-format#1614](https://github.com/palantir/palantir-java-format/issues/1614)). Spotless then stops with `expected token: '_'`. Name the variable instead: `case ProductItemAdded(var shoppingCartId, var productItem)`. Other unnamed variables, such as `_ -> ...` in a lambda or `case Initial _`, format fine.

Several READMEs show code in Java blocks, for example in `samples/distributed-processes`, `samples/events-versioning` and `samples/uniqueness`. The blocks are copies of the code, so when you change that code, update the block too. Otherwise the README teaches code that no longer exists.

## Tests

Tests are the specification of the code. Each test describes one usage scenario from the reader's perspective: what a caller does and what it observes. Name the test after that behavior, not after the method it calls, for example `placingAPlacedOrderEmitsNothing` or `cancelOrder_fails_withNotFound_forUnknownOrder`. A reader should learn what the code does from the test names and bodies alone. Tests written this way survive refactors and read as examples.

We reach the code from the outside in, through the same entry points that calling code uses, and write each test as Given / When / Then:

- Domain logic goes through `AggregateSpecification` or `EventSourcedSpecification`: given the past events, when a method runs, then these events follow. [`OrderTests`](./samples/distributed-processes/src/test/java/io/eventdriven/distributedprocesses/ecommerce/orders/OrderTests.java) is a good place to start.
- The web API goes through `ApiSpecification`, which starts the application on a random port and calls the real endpoints. Set up the state through the API too, not by writing into storage. For example, [`CancelOrderTests`](./samples/distributed-processes/src/test/java/io/eventdriven/distributedprocesses/ecommerce/api/CancelOrderTests.java) uses `PurchaseRestBuilder` to check out or pay an order before it cancels it.

Don't use a mocking library. When a test needs another service, write a fake that implements the real interface, and name it after what it does: `SilentPaymentGateway` takes a capture but never answers it, `ThrowingPaymentGateway` fails every call as an unreachable provider, and `FailingFirstCapturePaymentGateway` refuses the first capture and grants the ones that follow. The test then reads as the scenario it checks.

Write assertions with [AssertJ](https://assertj.github.io/doc/), not JUnit's `Assertions`. Put the value under test in `assertThat` and the expected value in the check, for example `assertThat(progressOf(orderId)).isEqualTo("Canceled")`, so the line reads as the sentence it checks and a failure names the right side. Expect an exception with `assertThatThrownBy(() -> ...).isInstanceOf(...)`. When code must not throw, call it directly: an exception fails the test anyway.

Use real databases, through `TestDatabases` and Testcontainers. Give each test its own new ids (`UUID.randomUUID()`), so tests can share one database without cleaning it up.

## Agent skills

Shared skills live in `.agents/skills/<name>/SKILL.md`. That file is the canonical body, so edit the skill there.

Codex and OpenCode read `.agents/skills/` directly. Claude Code does not, so `.claude/commands/<name>.md` holds a thin pointer that reads the skill file. OpenCode also gets a pointer in `.opencode/commands/<name>.md`, which exposes the skill as a `/name` slash command.

When adding a skill, write the body in `.agents/skills/` with `name` and `description` in the front matter, then add both pointer files.

See `.agents/README.md` for how skills and the change-check hooks work. Run the tests of the hooks, the pre-commit hook and the VS Code task script from the repository root with `node --test .agents/hooks/*.spec.ts .opencode/plugins/*.spec.ts .githooks/*.spec.ts .vscode/*.spec.ts`.

## Verification

The completion hook runs `./gradlew spotlessApply` and `./gradlew testClasses` automatically in each Gradle build (a folder with `gradlew`) edited in the session.

For documentation or agent-configuration changes, validate only the changed files and configuration. The Gradle build is not required.

While changing code, identify and run the smallest relevant set of tests covering the affected behavior. Before reporting a development phase as complete, run the same commands as the folder's CI workflow in `.github/workflows`. For most folders, that is `./gradlew build`, but some check more: `samples/distributed-processes` also runs `./gradlew eventCatalogTest`. If a build is interrupted, ask before running it again.

Report which verification commands were run, whether they passed, and why any relevant checks were skipped.

## Working rules

- Use simple, direct, straightforward language (STE English). Keep explanations concise, example-based, and focused on the problem. Simplify without oversimplifying.
- If a requirement or intended behavior is unclear, ask instead of assuming.
- Do not monkey-patch globals, prototypes, or third-party modules. If a change appears to require monkey-patching, stop and ask first.
- Prefer simple, easily removable code over premature abstractions. Do not introduce a new abstraction unless explicitly requested.
- Do not use comments inside the code unless asked. If you want to do it, review code and make it self-explanatory instead.
- Use a test-first approach for behavior changes. Name tests after observable behavior from the usage perspective, not implementation details.
- Treat questions, design discussions, and requests for opinions as read-only. Wait for explicit approval before editing files or running mutating commands.
- Never reverse a decision already agreed to (a requirement, a behaviour, a test that guards it) unless explicitly approved by that specific reversal. Ask user and explain tradeoffs.
- Plan to use subagents, when available, for independent, non-sequential work that can usefully run in parallel, such as research, repository exploration, focused implementation, verification, and review. Keep user interaction, cross-cutting decisions, and final synthesis in the main context. Ask for that usage before sending them. Don't start subagents with inherit context. Give subagents sufficient context and require concrete, evidence-backed findings so important details are not lost.
- Clean up what you started before you report a task as done: stop the containers, Docker Compose services, Gradle daemons and other background processes you started, remove the images you pulled only for a check, and delete your temporary files, copies and scratchpad content. Leave what was there before you started, such as containers of other projects or the shared Gradle and npm caches, and report what you removed.
- Do not commit, create branches or tags, rebase, reset, or otherwise modify Git history unless explicitly requested.
- Do not add manual line breaks inside Markdown paragraphs. Keep each paragraph on a single line and let the renderer or editor wrap it.
