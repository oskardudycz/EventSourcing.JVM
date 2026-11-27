# Q&A: EDA workshop exercises on the e-commerce sample

## Idea

ok, so I'm running the workshop about the light the dark side of EDA, here's the outline: https://event-driven.io/en/light-and-dark-side-of-eda-workshop/

samples/e-commerce - shows an advanced way to do that, read README about capabilities,

I'd like to make it possible to be run by people (also not having Java installed) and try to learn from it, so techniques like Observability, Otel, troubleshooting, topology, long polling and other techniques dealing with eventual consistency, idempotency, delivery guarantees, etc, documentation of EDA, compensation, and possibly using LLMs to help in understanding system.

Currently all messaging comes through messaging bus, which could allow us to pass to gradlew command to simulate some breaking scenario and ask people to debug it, e.g. using graphana, maybe some mcp servers (like event catalog, spring boot) and some visualisation.

COuld you research based on what you have what we could do to design exercises, and what people to use and how to give "deterministic non-deterministic failure" that people could troubleshoot and or, how to learn and understand system with potential soltuions?

## Q1

The workshop page lists Week 2 for 30 November and 1 December, three days from now. Is this for that session, or for a later run? If it's Monday, I'd cut the scope to what's quick to build: Toxiproxy, the sandbox failures that already exist, and Grafana. A custom fault layer and MCP setup would be too much for three days.

**Answer:** Dude, firstly did you even read it? And did you research in the internet as asked?

**Note:** Claude asked the question before doing the research that the idea asked for. The question is dropped. The research runs in four subfolders: `running-without-java`, `fault-injection`, `mcp-and-visualisation`, `codebase-hooks`.

## Remark during the research

**Oskar:** are you also keeping the log? And using local brainstorm rule? I don't see any qa.md

**Note:** `qa.md` started late. From here on, each question and its verbatim answer goes here before the next question.

## Q2

Oskar asked: "Can't you do research on your own?" Claude answered that it can, and that it used subagents because the brainstorm skill asks for them. Claude asked whether to stop the four agents and research itself, which breaks the skill's rule.

**Answer:** I'd rather you did it

**Note:** Claude stopped the four agents. Claude does the research itself and writes the findings to the same topic subfolders.

## Decision during the research

**Oskar:** No codespaces please, locally

**Note:** Participants run everything on their own machines. Codespaces and other cloud environments are out of scope.

## Remark during the research

**Oskar:** Dude tigerbeetle deterministic, are you fng joking?

**Note:** Deterministic simulation testing of databases (TigerBeetle VOPR, Jepsen, Antithesis) is off-topic. "Deterministic non-deterministic failure" means a scripted incident in the running sample: it looks random to participants, but the trainer knows it and it happens the same way on every machine. Claude removed Maelstrom and Antithesis from the findings.

## Q3

Claude asked whether to continue with the ARM64 KurrentDB image, an app container image, EventCatalog in a container and the Tempo service graph.

**Answer:** Read also https://medium.com/confluent/preventing-and-fixing-bad-data-in-event-streams-part-1-27bf2a99b48e
and
https://medium.com/confluent/preventing-and-fixing-bad-data-in-event-streams-part-2-526e459c7c6f
and
https://medium.com/ssense-tech/exploring-advanced-error-handling-patterns-with-event-driven-architecture-part-i-e2f37741d904
and
https://medium.com/ssense-tech/exploring-advanced-error-handling-patterns-with-event-driven-architecture-part-ii-235bb72cfa19
and
https://levelup.gitconnected.com/error-handling-in-event-driven-systems-1f0a7ef2cfb7

And my articles linked in README

Focus on the real stuff and real things we already show here in the repo and how could we make them interactive exercises I already gave you clear hints you seem to ignore

## Remark during the research

**Oskar:** Check this https://www.confluent.io/blog/shift-left-bad-data-in-event-streams-part-1/ and https://www.confluent.io/blog/shift-left-bad-data-in-event-streams-part-2/ then

**Note:** These are the Confluent versions of the two Medium articles that answered HTTP 403.

## Remarks during the research

**Oskar:** Wtf "most"?

**Note:** Claude had said it had read "most" of the two Confluent articles. It then read both in full.

**Oskar:** store the whole context or articles you read

**Note:** The texts of all articles read are in `articles/sources/`, and the READMEs and docs of the tools in `tools-and-versions/sources/`.

## Q4

Claude listed findings and cautions, then asked how a scenario should break the system: A, a decorator around `MessageBus` and `EventStore` chosen by a property; B, external tools such as Toxiproxy and Pumba; C, both.

**Answer:** Some alerts can't fire live. The sandbox capture always succeeds, so Capture failed and Money lost fire only in the tests today. - we'd need to simulate it, that's why we have sandboxes.

Deadlines are too long for a session. They are hard-coded at 24 hours and 7 days, and the workers run every minute. - same here.

No traffic. Nothing runs purchases on its own, so the rate-based alerts never fire. - our scenarios should give some setup for the example data, some exercises should also ask user or give them option to manually use app.

Don't commit articles/sources/bellemare-*.txt to the public repo. Those are Confluent's copyrighted texts. Your own articles are fine. - I won't commit them, just keep it there for the reference cache, I'll delete them. Credit them, not name as mine.

The four folders the stopped agents left (codebase-hooks, mcp-and-visualisation, running-without-java, fault-injection) contain unchecked partial output.  - why didn't you do it on your own then?

A - probably the simplest and the morst powerful. Unless it doesn't allow us to simulate something. We could even simulat kafka downtime there, right? We could use both if those other helps, but I'm unsure

**Decisions taken from the answer:**

- The sandboxes simulate the outside failures, such as a refused capture, as part of the scenarios.
- The deadlines become settable for the workshop, the same way.
- Each scenario sets up example data (traffic) itself. Some exercises ask participants to use the app by hand, or offer it as an option.
- Third-party article texts stay only as a local reference cache, credited to their authors. Oskar deletes them before committing.
- Option A, the decorator, is the main way to break the system. External tools only where they add something A can't do.

## Q5

Claude asked whether participants should know what a scenario breaks: open names, hidden incidents with a symptom ticket, or both.

**Answer:** Let's start first by how would you ask people to run it without java, then we can decide, but imho it should be some gradlew dedicated task that'd do needed setup

## Q6

Claude explained that `./gradlew` needs a JVM to start, and proposed Docker Compose (`docker compose --profile workshop up`, scenario in `WORKSHOP_SCENARIO`) for everyone, with a Gradle task that builds the image from local code for people with a JDK. Claude asked whether Oskar agrees, or wants everyone to go through one command.

**Answer:** We can name exercises by stuff they need to learn but without giving info on what to solve, we can also have alias without it, right?

**Note:** The answer is about naming the scenarios (the question from Q5). The question about the entry point stays open.

## Q7

Claude explained the two names per scenario (topic name and alias) and asked again whether Docker Compose is the way in for everyone, with the Gradle task only for people with a JDK.

**Answer:** So we'd build a docker image from app and run together with docker compose? we could also direct it by env variable, or what other option is not to rebuild it?

## Q8

Claude described three ways to pick a scenario without rebuilding the image: (1) an environment variable at start, (2) a switch while the app runs (a page under Developer tools or an endpoint), (3) a watched mounted file. Claude recommended 1 and 2, and asked whether a scenario should be on from the start only, or also switchable at run time.

**Answer:** definitely 1 and maybe 2 depending on scenario

**Decision:** Every scenario can be picked at start with `WORKSHOP_SCENARIO`. Some scenarios also offer a switch at run time, where the exercise needs it.

## Q9

Claude asked whether the example data should come from (A) a shopper outside the app, a container that buys as fixed customers through the HTTP API and the sandbox pages, or (B) a seeder inside the app that calls the facades and publishes on the bus. Claude recommended A, as a second main class in the same image.

**Answer:** A

**Decision:** A shopper outside the app creates the example data, through the same entry points as a person: the shop's HTTP API and the sandbox pages.

## Q10

Claude listed 15 draft exercises (getting around, compensation, idempotency, delivery guarantees, deadlines, eventual consistency, ordering, contracts, tracing, duplicates by design, LLM) with topic names and aliases, and asked which go into the first version.

**Answer:** I'd introduce 14 and 15 earlier, I'd also ask people to find out the correlation and causation and learn on propagation.

Also at-least once, event store subscription, it'd be also good to think if we can expand samples to show message versioning.

ALso i don't see event catalog here it'd be good if people learn about the tools they could use and troubleshoot. Maybe we should also add some kafka ui and pg admin and/or kurrent db ui (although I wouldn't focus too much on Kurrent, it's more as a showcase that event sourcing on its own could help to show also business observability together with otel, and that event store subscription works as outbox.

Maybe we could showcase some our MCP that would correlate data from event store and traces, also it'd be nice to showcase how exports traces from tests, and e2e test works and how could it troubleshoot

Thoughts?

**Added during the answer:** I'd like to also showcase race conditions

## Q11

Claude gave thoughts on each point of Q10, saved a four-part outline in `exercises-from-the-repo/outline-draft.md`, and asked whether the order in four parts works.

**Answer:** What order, i see mumbling not suggestions.

**Note:** The reply did not show the order itself. Claude lists it in the next message.

## Q12

Claude listed the order: Part 1 (getting-around, documentation-1, correlation-1, duplicates-by-design-1, llm-1), Part 2 (at-least-once-1 to versioning-1), Part 3 (incidents by alias), Part 4 (tests), and asked whether to keep it.

**Answer:** Where's command handling idempotence? Compare also to what's described in README

## Q13

Claude added four idempotency exercises (commands twice, Pay now twice, late answer for an old payment, duplicated event) and a table mapping the README's idempotency statements to exercises, and asked whether to keep the four or merge some.

**Answer:** I mean, do they need to be each separate exercise? Can't we group concepts into the same ones?

## Q14

Claude proposed nine grouped exercises (tools, correlation, idempotency, delivery-guarantees, concurrency-and-consistency, compensation-and-deadlines, versioning, incidents, tests) and asked whether the groups and order work.

**Answer:** Ok, sounds good, store this outline-draft. And then provide

Given: Conditions and how this will be setup and 
When: what will be run and what issue will be done
Then: What's the error and how this could be or is fixed and how attendee should diagnose that?

Each exercise could have multiple acceptance criteria

**Decision:** The nine exercises in this order are agreed. Each exercise gets acceptance criteria in Given / When / Then form.

## Q15

Claude stored the outline and 26 acceptance criteria, noted a correction to AC 6.1 (an out-of-stock order waits for the payment's answer), and asked whether versioning and the timeline MCP belong in the first version.

**Answer:** One correction while writing. I first wrote that an out-of-stock order is canceled at once. The README says otherwise: "A failed shipment cancels the order, but only once the payment has answered". So AC 6.1 now says the order waits until the customer pays or the payment times out. - check tests for that.

Q: may come in 1.1.

We still didn't discuss what tools will we use and how, (e.g. which mcp or what)

**Decision:** Versioning and the timeline MCP come in version 1.1, not in the first version.

## Q16

Claude asked whether to package the KurrentDB MCP (a Python script, no Docker image) in our own image, or leave it out of the first version.

**Answer:** I mean it depedns, if it brings value then yes, but it'd be good if we don't ask anyone to have python installed (although they probably do so it depends on fte struggle).

## Q17

Claude recommended leaving the KurrentDB MCP out of version 1 (its read_stream returns no metadata, and it has write tools with no read-only switch), with Grafana MCP and the EventCatalog files as the LLM's tools, and asked whether Oskar agrees.

**Answer:** fine by me, what about spring mcp?

**Decision:** The KurrentDB MCP is out of version 1.

## Q18

Claude described a Spring MCP server with Spring AI 2.0.3 inside the app, with read-only tools: (1) read a stream with its events' metadata, (2) subscriptions with their checkpoints, (3) the timeline joining events and spans per order. Claude asked whether tools 1 and 2 go into version 1 and the timeline into 1.1, or the whole Spring MCP waits for 1.1.

**Answer:** What's actuator? then if 3 is the option then sounds like 1.0 to me

**Note:** This moves the timeline MCP from version 1.1 (Q15) to version 1.0. Claude asks Oskar to confirm the reversal.

## Q19

Claude explained what Spring Boot Actuator is, and asked Oskar to confirm that the timeline MCP moves to version 1.0 with the other two read-only tools (a stream with metadata, the subscriptions with their checkpoints), while versioning stays in 1.1.

**Answer:** Yes

**Decision:** Version 1.0 has a Spring MCP server in the app (Spring AI 2.0.3) with three read-only tools: read a stream with its events' metadata, the subscriptions with their checkpoints, and the timeline joining events and spans per order. This reverses the part of Q15 that put the timeline MCP in 1.1. Versioning stays in 1.1.

## Q20

Claude raised that the repo is public, so answer keys next to the exercises reveal the incidents, and offered A (everything in this repo), B (answer keys private) or C (everything private until after the workshop), recommending B.

**Answer:** This repo is public. If the answer keys sit next to the exercises, people can read which customer gets which fault, and the incidents are solved before they start.  - this is fine, check other workshops

**Note:** The other workshops in this repo (`workshops/*/exercises` and `workshops/*/solved`) keep the exercises and their full solutions next to each other in the public repo, and call themselves a self-paced kit. Each exercise has a `README.md` with the context, a Goal section, the steps and links to the tests that check it. So option A: everything in this repo, answer keys included.

**Decision:** Exercises, tickets, hints and answer keys all live in this public repo, as in the other workshops.

## Q21

Claude asked whether the workshop material lives inside the sample, in `samples/e-commerce/workshop/` with one folder per exercise (`README.md` and `SOLUTION.md`), or in a new `workshops/light-and-dark-side-of-eda/` folder.

**Answer:** A

**Decision:** The material lives in `samples/e-commerce/workshop/`: a `README.md` listing the exercises and the prerequisites (Docker only), and one folder per exercise (`e01_tools/`, `e02_correlation/`, ...) with `README.md` (context, goal, tasks, ticket) and `SOLUTION.md`. The root `README.md` and the workshops list link to it.

## Q22

Claude asked how the app image is published: (A) GitHub Container Registry from CI, multi-arch, tagged latest and the commit SHA, with a fixed tag per workshop run; (B) Docker Hub from CI; (C) no registry, participants build it with a multi-stage Dockerfile and `docker compose build`.

**Answer:** C and A

**Decision:** Both. CI publishes the image to GHCR (A), and the sample also has a multi-stage Dockerfile, so participants can build the image locally with Docker only (C).

## Q23

Claude proposed one Dockerfile for both CI (docker buildx, amd64 and arm64) and local builds, so `bootBuildImage` is not needed, and asked how to handle KurrentDB on Apple Silicon: (A) keep KurrentDB with an `.env` line for the experimental ARM64 tag, (B) use the PostgreSQL event store in the workshop profile with KurrentDB as an option, (C) A plus a test on a Mac before the workshop.

**Answer:** So we publish docker images on push, but also allow to build it.

B

**Decision:** CI publishes the image on each push; participants can also build it locally from the same Dockerfile. The workshop profile uses the PostgreSQL event store (`eventstore.type=postgresql`); KurrentDB is an option, not part of every exercise.

## Q24

Claude asked which brokers the workshop uses: (A) Kafka only, (B) Kafka by default with RabbitMQ as a switch, every scenario working on both, (C) B but only some exercises on RabbitMQ.

**Answer:** B

**Decision:** Kafka by default; `MESSAGING_BUS=rabbitmq` switches to RabbitMQ. Every scenario works on both brokers, and the exercise docs give both variants where the brokers differ (Kafbat UI for Kafka, the RabbitMQ management UI for RabbitMQ).

## Q25

Claude asked how the shopper behaves: (A) a fixed script per scenario that then stops, (B) steady background traffic of generated customers, (C) both: steady background traffic plus the scenario's named customers at fixed moments after the start.

**Answer:** C

**Decision:** The shopper runs steady background traffic of generated customers (for example one purchase every 10 to 20 seconds, with a fixed share of declined cards, out-of-stock products and rejects), enough for the rate-based alerts. On top, each scenario has named customers who appear at fixed moments after the start; the faults hit only them.

## Q26

Claude asked how to verify the exercises: (A) unit and integration tests for the new parts plus one test per scenario that checks the symptom from `SOLUTION.md` on Kafka and RabbitMQ, or (B) A plus browser tests that go through the by-hand steps of each exercise in Chromium and save the screenshots for the exercise READMEs.

**Answer:** B

**Decision:** Unit and integration tests for the decorators, the sandbox faults, the settable deadlines, the shopper and the Spring MCP tools; one test per scenario checking the documented symptom on Kafka and on RabbitMQ; and browser tests that walk the by-hand steps of each exercise and save the screenshots for its README, run apart from `build` like `e2eTest`.

## Q27

Claude asked which LLM clients get a ready MCP config: (A) Claude Code and VS Code with Copilot, (B) A plus Cursor, (C) only a generic snippet in the workshop README.

**Answer:** Claude Code, Codex, Open Code.

**Decision:** The sample ships MCP config for Claude Code, Codex and OpenCode.

## Q28

Claude listed where Claude Code (`.mcp.json`), Codex (`.codex/config.toml`, trusted projects only) and OpenCode (`opencode.json`, `mcp` key) read MCP config, proposed defaults for short deadlines, Grafana MCP access and the run-time switch, and asked whether to write the specification.

**Answer:** We already have setup in root we could copy, right?

## Q29

Claude explained that the root has an agent setup for Claude Code, Codex and OpenCode (AGENTS.md, skills in `.agents` with pointers, change-check hooks per client) but no MCP config, recommended putting the MCP config in `samples/e-commerce`, and asked whether Oskar is ready for the specification with the defaults: short deadlines (payment validity 5 minutes, authorization hold 10 minutes, payment terms 10 minutes, workers every 15 seconds), Grafana MCP through anonymous access or else the local admin user, and a run-time switch page under Developer tools backed by an endpoint.

**Answer:** yes, people should just open e-commerce folder, so all setup should be netested there

**Decision:** Participants open only the `samples/e-commerce` folder. All their setup lives there: the MCP config for Claude Code (`.mcp.json`), Codex (`.codex/config.toml`) and OpenCode (`opencode.json`), and any agent instructions they need. The three defaults are accepted. Claude writes the specification next.

## Q30

Oskar, before I write the spec, the skill asks me to check the test seams with you: the points where tests drive the new features from outside. I prefer seams that already exist, at the highest level possible:

1. **The running app from outside, as today.** The scenario tests extend the `PurchaseTests` pattern: `ApiE2ESpecification` with `workshop.scenario=<name>` set as a property, run in two classes, one on Kafka and one on RabbitMQ (like `PurchaseOnKafkaTests` and `PurchaseOnRabbitMQTests`, with the PostgreSQL event store). A test plays the named customer through HTTP and checks the documented symptom with `expectTrace`, `expectPublished`, `expectMetric` and the order's state. This one seam covers the bus and store decorators, the short deadlines (the test clock moves time, as today) and the run-time switch endpoint.
   - **One catch:** today `OutsideWorld` replaces the sandboxes with scriptable fakes. The sandbox faults (a refused capture, a webhook that never comes) live in the sandboxes. So their scenario tests have to run with the real sandboxes, and drive them through their own pages or endpoints, the way a person does.
2. **The browser, as today.** `BrowserE2ESpecification` walks the by-hand steps of each exercise in Chromium and saves the screenshots for the READMEs, like `ShopInBrowserTests` does for HOWTO.md.
3. **The shopper, from outside.** A test starts the app and the shopper, lets the shopper run for a short, test-controlled time, and checks through the app's HTTP API that the background customers bought and that the named customers appeared at their moments. This is a new seam, because the shopper is a new process.
4. **The Spring MCP, through the MCP protocol.** A test connects an MCP client to the running app's MCP endpoint, calls the three tools for a purchase that the test made, and checks the answers: events with their correlation, causation and trace ids, subscriptions with checkpoints, and the timeline. This is a new seam, because MCP is a new way into the app.
5. **The workshop setup, as one smoke test.** CI builds the image from the Dockerfile, starts `docker compose --profile workshop up`, and checks that the shop, Grafana, EventCatalog, the Kafka UI and pgAdmin answer, once on Kafka and once with `MESSAGING_BUS=rabbitmq`. This is new, because no test covers the Compose setup today.

That's two existing seams (1, 2) and three new ones (3, 4, 5). I couldn't avoid the new ones: each is a new entry point that the existing seams don't reach.

**Question:** Do these seams match what you expect, including running the sandbox-fault scenarios with the real sandboxes instead of `OutsideWorld`?