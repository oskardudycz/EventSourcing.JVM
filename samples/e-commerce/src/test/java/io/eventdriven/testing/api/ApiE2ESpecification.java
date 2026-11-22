package io.eventdriven.testing.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.testing.Transcript;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonDifferenceCalculator;
import org.jspecify.annotations.Nullable;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.http.ETag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureRestTestClient
public abstract class ApiE2ESpecification {
    private static final JsonMapper json = JsonMapper.builder().build();
    private static final Duration traceTimeout = Duration.ofSeconds(3);
    private static final Map<MessageBus, List<Object>> publishedOn = Collections.synchronizedMap(new WeakHashMap<>());

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired(required = false)
    protected @Nullable MessageBus messageBus;

    @Autowired(required = false)
    protected @Nullable InMemorySpanExporter spans;

    @Autowired(required = false)
    protected @Nullable EventStore eventStore;

    public interface TestStep {}

    @FunctionalInterface
    public interface TestRequest extends TestStep, Function<RestTestClient, RestTestClient.RequestHeadersSpec<?>> {}

    @FunctionalInterface
    public interface TestAction extends TestStep {
        void run();
    }

    public record Publish(List<Object> messages) implements TestStep {}

    public record Attempt(TestRequest request) implements TestStep {}

    public record TestResponse(HttpStatusCode status, HttpHeaders headers, String body) {
        public JsonNode json() {
            return json.readTree(body);
        }
    }

    public record Outcome(@Nullable TestResponse response, List<Object> published, Supplier<List<SpanData>> spans) {}

    @FunctionalInterface
    public interface Expectation {
        void verify(Outcome outcome);
    }

    public sealed interface ResponseExpectation {
        record Body(Object expected) implements ResponseExpectation {}

        record Headers(Map<String, String> expected) implements ResponseExpectation {}

        record Satisfies(Consumer<TestResponse> check) implements ResponseExpectation {}
    }

    public record ExpectedSpan(String name, List<ExpectedSpan> children) {}

    protected boolean isAbout(Object message) {
        return true;
    }

    protected Given given(TestStep... steps) {
        return new Given(this, List.of(steps));
    }

    protected Given given(TestStep[] scenario, TestStep... moreSteps) {
        return given(scenario(scenario, moreSteps));
    }

    public record Given(ApiE2ESpecification specification, List<TestStep> steps) {
        public When when(TestStep step) {
            return new When(this, step);
        }
    }

    public record When(Given given, TestStep step) {
        public void then(Expectation... expectations) {
            var specification = given.specification();
            var traceIds = ConcurrentHashMap.<String>newKeySet();

            for (var givenStep : given.steps()) {
                var response = specification.run(givenStep, traceIds);

                if (response != null && !(givenStep instanceof Attempt))
                    assertThat(response.status().isError())
                            .as("Given request failed with %s: %s", response.status(), response.body())
                            .isFalse();
            }

            var published = specification.published();
            var messagesBefore = published.size();

            var response = specification.run(step, traceIds);

            var outcome = new Outcome(
                    response,
                    published.subList(messagesBefore, published.size()).stream()
                            .filter(specification::isAbout)
                            .toList(),
                    () -> specification.finishedSpans().stream()
                            .filter(span -> traceIds.contains(span.getTraceId()))
                            .toList());

            for (var expectation : expectations) {
                expectation.verify(outcome);
            }
        }
    }

    public static TestStep[] scenario(TestStep[] scenario, TestStep... moreSteps) {
        var steps = new ArrayList<TestStep>(List.of(scenario));
        steps.addAll(List.of(moreSteps));
        return steps.toArray(TestStep[]::new);
    }

    public static TestStep attempt(TestRequest request) {
        return new Attempt(request);
    }

    public static TestStep publish(Object... messages) {
        return new Publish(List.of(messages));
    }

    public static Expectation expectResponse(HttpStatus status, ResponseExpectation... expectations) {
        return outcome -> {
            var response = outcome.response();

            assertThat(response)
                    .as("The when step got no response, because it published messages instead of sending a request")
                    .isNotNull();
            assertThat(response.status())
                    .as("Response status, with body: %s", response.body())
                    .isEqualTo(status);

            for (var expectation : expectations) {
                switch (expectation) {
                    case ResponseExpectation.Body(var expected) ->
                        JsonAssert.comparator(JSONCompareMode.STRICT_ORDER)
                                .assertIsMatch(json.writeValueAsString(expected), response.body());
                    case ResponseExpectation.Headers(var expected) ->
                        expected.forEach(
                                (name, value) -> assertThat(response.headers().getFirst(name))
                                        .as("Header %s", name)
                                        .isEqualTo(value));
                    case ResponseExpectation.Satisfies(var check) -> check.accept(response);
                }
            }
        };
    }

    public static Expectation expectError(HttpStatus status) {
        return expectResponse(status);
    }

    public static Expectation expectError(HttpStatus status, Map<String, ?> problemDetails) {
        return expectResponse(status, body(problemDetails));
    }

    public static Expectation expectPublished(Object... messages) {
        var expected = List.of(messages);

        return outcome -> assertThat(isSubsequence(expected, outcome.published()))
                .as("%s", """
                        Expected these messages, in this order:
                        %s
                        Published messages:
                        %s""".formatted(Transcript.numbered(expected), Transcript.numbered(outcome.published())))
                .isTrue();
    }

    public static Expectation expectOnlyPublished(Object... messages) {
        var expected = List.of(messages);

        return outcome -> assertThat(outcome.published())
                .as("%s", """
                        Expected only these messages:
                        %s
                        Published messages:
                        %s""".formatted(Transcript.numbered(expected), Transcript.numbered(outcome.published())))
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    public static Expectation expectNothingPublished() {
        return expectOnlyPublished();
    }

    public static Expectation expectTrace(ExpectedSpan root) {
        return outcome -> {
            var deadline = System.nanoTime() + traceTimeout.toNanos();
            var mismatch = mismatchOf(root, outcome.spans().get());

            while (mismatch.isPresent() && System.nanoTime() < deadline) {
                sleep();
                mismatch = mismatchOf(root, outcome.spans().get());
            }

            assertThat(mismatch)
                    .as(
                            "%s%nFinished spans:%n%s",
                            mismatch.orElse(""), describe(outcome.spans().get()))
                    .isEmpty();
        };
    }

    public static ExpectedSpan span(String name, ExpectedSpan... children) {
        return new ExpectedSpan(name, List.of(children));
    }

    public static ResponseExpectation body(Object expected) {
        return new ResponseExpectation.Body(expected);
    }

    public static ResponseExpectation headers(Map<String, String> expected) {
        return new ResponseExpectation.Headers(expected);
    }

    public static ResponseExpectation satisfying(Consumer<TestResponse> check) {
        return new ResponseExpectation.Satisfies(check);
    }

    public static String weakETag(long version) {
        return new ETag(String.valueOf(version), true).formattedTag();
    }

    private @Nullable TestResponse run(TestStep step, Set<String> traceIds) {
        var response = runOnly(step, traceIds);
        whenCaughtUp();
        return response;
    }

    private void whenCaughtUp() {
        var published = published();
        int seen;
        do {
            seen = published.size();
            if (messageBus != null) messageBus.whenCaughtUp();
            if (eventStore != null) eventStore.whenCaughtUp();
        } while (published.size() != seen);
    }

    private @Nullable TestResponse runOnly(TestStep step, Set<String> traceIds) {
        switch (step) {
            case TestRequest request -> {
                var traceId = randomHex(16);
                traceIds.add(traceId);
                var result = request.apply(restTestClient)
                        .header("traceparent", "00-%s-%s-01".formatted(traceId, randomHex(8)))
                        .exchange()
                        .returnResult();
                var body = result.getResponseBodyContent();

                return new TestResponse(
                        result.getStatus(),
                        result.getResponseHeaders(),
                        body == null ? "" : new String(body, StandardCharsets.UTF_8));
            }
            case Attempt(var request) -> {
                return runOnly(request, traceIds);
            }
            case Publish(var messages) -> {
                assertThat(messageBus).as("Publishing needs a message bus").isNotNull();
                messageBus.publish(messages);
                return null;
            }
            case TestAction action -> {
                action.run();
                return null;
            }
            default -> throw new IllegalArgumentException("Unknown test step: " + step);
        }
    }

    private List<Object> published() {
        if (messageBus == null) return List.of();

        return publishedOn.computeIfAbsent(messageBus, bus -> {
            var recorded = new CopyOnWriteArrayList<Object>();
            bus.use(recorded::add);
            return recorded;
        });
    }

    private List<SpanData> finishedSpans() {
        return spans == null ? List.of() : spans.getFinishedSpanItems();
    }

    private static boolean isSubsequence(List<Object> expected, List<Object> published) {
        var differences = new RecursiveComparisonDifferenceCalculator();
        var configuration = new RecursiveComparisonConfiguration();
        var next = 0;

        for (var message : published) {
            if (next < expected.size()
                    && differences
                            .determineDifferences(message, expected.get(next), configuration)
                            .isEmpty()) next++;
        }
        return next == expected.size();
    }

    private static Optional<String> mismatchOf(ExpectedSpan expected, List<SpanData> spans) {
        var candidates = spans.stream()
                .filter(span -> span.getName().equals(expected.name()))
                .toList();

        if (candidates.isEmpty()) return Optional.of("No span '%s'".formatted(expected.name()));

        Optional<String> firstMismatch = Optional.empty();
        for (var candidate : candidates) {
            var mismatch = mismatchBelow(expected, candidate, spans);
            if (mismatch.isEmpty()) return Optional.empty();
            if (firstMismatch.isEmpty()) firstMismatch = mismatch;
        }
        return firstMismatch;
    }

    private static Optional<String> mismatchBelow(ExpectedSpan expected, SpanData parent, List<SpanData> spans) {
        for (var child : expected.children()) {
            var candidates = spans.stream()
                    .filter(span -> span.getName().equals(child.name()) && isBelow(span, parent, spans))
                    .toList();

            if (candidates.isEmpty())
                return Optional.of("No span '%s' below '%s'".formatted(child.name(), expected.name()));

            Optional<String> firstMismatch = Optional.empty();
            var matched = false;
            for (var candidate : candidates) {
                var mismatch = mismatchBelow(child, candidate, spans);
                if (mismatch.isEmpty()) {
                    matched = true;
                    break;
                }
                if (firstMismatch.isEmpty()) firstMismatch = mismatch;
            }
            if (!matched) return firstMismatch;
        }
        return Optional.empty();
    }

    private static boolean isBelow(SpanData span, SpanData ancestor, List<SpanData> spans) {
        if (!span.getTraceId().equals(ancestor.getTraceId())) return false;

        var byId = spans.stream()
                .filter(each -> each.getTraceId().equals(ancestor.getTraceId()))
                .collect(Collectors.toMap(SpanData::getSpanId, each -> each, (first, second) -> first));
        var parentId = span.getParentSpanId();

        while (byId.containsKey(parentId)) {
            if (parentId.equals(ancestor.getSpanId())) return true;
            parentId = byId.get(parentId).getParentSpanId();
        }
        return parentId.equals(ancestor.getSpanId());
    }

    private static String describe(List<SpanData> spans) {
        var lines = new ArrayList<String>();
        for (var span : spans) {
            lines.add("  %s (trace %s, span %s, parent %s)"
                    .formatted(span.getName(), span.getTraceId(), span.getSpanId(), span.getParentSpanId()));
        }
        return String.join("\n", lines);
    }

    private static String randomHex(int bytes) {
        var random = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(random);
        return HexFormat.of().formatHex(random);
    }

    private static void sleep() {
        pause(Duration.ofMillis(50));
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
