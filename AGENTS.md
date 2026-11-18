# EventSourcing.JVM

Tutorials, workshops and samples about Event Sourcing and event-driven design on the JVM. See `README.md` for what each part teaches.

## Project structure

The repository has no root build. Each sample and each workshop folder is its own Gradle build with its own wrapper, CI workflow, dev container and VS Code setup:

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

The `exercises` folders hold the workshop tasks, and the `solved` folders hold their solutions.

## Setup

The quickest start is the dev container. Open the repository root or one build folder in VS Code and run **Dev Containers: Reopen in Container**. A build folder's container mounts the whole repository, so Git and the hooks work, and opens that folder. The container uses the host's Docker for Testcontainers.

To work on your own machine, install Java 17 or newer to start Gradle, and Docker for the tests that use KurrentDB, PostgreSQL or MongoDB. Gradle runs itself on Java 22 (`gradle/gradle-daemon-jvm.properties`) and compiles with the Java 22 toolchain. It downloads Java 22 if it is missing.

The tests start their databases with Testcontainers. If `docker compose --profile kurrentdb up` (from the repository root) is running, the tests that support it use that KurrentDB instead.

### Everyday commands

From a build folder:

```shell
./gradlew build         # compile and run all tests
./gradlew testClasses   # compile only, the fast inner loop
./gradlew test --tests 'io.eventdriven.uniqueness.shoppingcarts.ShoppingCartTests' # run one test class
./gradlew spotlessApply # format the code
```

### Editor setup

**VS Code:** open a build folder, or the root workspace file `EventSourcing.JVM.code-workspace`. Accept the recommended extensions. Format on save then uses the build's Spotless settings. In a build folder, the tasks (_Ctrl+Shift+P → Run Task_) run in that build. At the root, they run in the build that holds the open file. **compile** is the default build task (Ctrl+Shift+B).

**IntelliJ IDEA:** open a build folder as a Gradle project and accept the prompt to install the **palantir-java-format** plugin.

## Code style

[Spotless](https://github.com/diffplug/spotless) formats Java with [palantir-java-format](https://github.com/palantir/palantir-java-format). The settings are in each `build.gradle`. `./gradlew build` does not check the formatting (`enforceCheck false`), so CI does not fail on it. The code is formatted in three places instead:

- format on save in VS Code and IntelliJ IDEA,
- the Git pre-commit hook, which runs `./gradlew spotlessApply` in each build with staged Java files and stages them again. Enable it once with `git config core.hooksPath .githooks` (the dev containers do it for you),
- the agent stop hook, see "Verification".

palantir-java-format can't format an unnamed variable (`_`) in a record pattern with `var`, for example `case ProductItemAdded(_, var productItem)` ([palantir-java-format#1614](https://github.com/palantir/palantir-java-format/issues/1614)). Name the variable instead.

Several READMEs copy code into Java blocks. When you change that code, update the block too.

## Agent skills

Shared skills live in `.agents/skills/<name>/SKILL.md`. That file is the canonical body, so edit the skill there.

Codex and OpenCode read `.agents/skills/` directly. Claude Code does not, so `.claude/commands/<name>.md` holds a thin pointer that reads the skill file. OpenCode also gets a pointer in `.opencode/commands/<name>.md`, which exposes the skill as a `/name` slash command.

When adding a skill, write the body in `.agents/skills/` with `name` and `description` in the front matter, then add both pointer files.

See `.agents/README.md` for how skills and the change-check hooks work. Run the tests of the hooks and the scripts from the repository root:

```shell
node --test .agents/hooks/*.spec.ts .opencode/plugins/*.spec.ts .githooks/*.spec.ts .vscode/*.spec.ts
```

## Verification

The completion hook runs `./gradlew spotlessApply` and `./gradlew testClasses` automatically in each Gradle build (a folder with `gradlew`) edited in the session.

For documentation or agent-configuration changes, validate only the changed files and configuration. The Gradle build is not required.

While changing code, identify and run the smallest relevant set of tests covering the affected behavior. Before reporting a development phase as complete, run the same commands as the build's CI workflow in `.github/workflows`, usually `./gradlew build`. If a build is interrupted, ask before running it again.

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
- Do not commit, create branches or tags, rebase, reset, or otherwise modify Git history unless explicitly requested.
- Do not add manual line breaks inside Markdown paragraphs. Keep each paragraph on a single line and let the renderer or editor wrap it.
