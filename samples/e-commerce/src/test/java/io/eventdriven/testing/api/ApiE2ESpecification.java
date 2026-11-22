package io.eventdriven.testing.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.testing.Transcript;
import io.eventdriven.testing.eventstore.RecordingEventStore;
import io.eventdriven.testing.tracing.FinishedSpans;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
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
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonAssert;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureRestTestClient
public abstract class ApiE2ESpecification {
    private static final JsonMapper json = JsonMapper.builder().build();
    private static final String MESSAGE_ID = "messaging.message.id";
    private static final String CORRELATION_ID = "messaging.message.correlation_id";
    private static final String CAUSATION_ID = "messaging.message.causation_id";
    private static final Map<MessageBus, List<MessageEnvelope<Object>>> publishedOn =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired(required = false)
    protected @Nullable MessageBus messageBus;

    @Autowired(required = false)
    protected @Nullable FinishedSpans spans;

    @Autowired(required = false)
    protected @Nullable EventStore eventStore;

    @Autowired(required = false)
    protected @Nullable MeterRegistry meters;

    protected Duration catchUpTimeout = Duration.ofSeconds(30);

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

    public record Outcome(
            @Nullable TestResponse response,
            List<MessageEnvelope<Object>> published,
            List<MessageEnvelope<Object>> appended,
            Supplier<List<SpanData>> spans,
            BiFunction<String, Map<String, String>, Double> metricIncrease) {}

    @FunctionalInterface
    public interface Expectation {
        void verify(Outcome outcome);
    }

    public sealed interface ResponseExpectation {
        record Body(Object expected) implements ResponseExpectation {}

        record Headers(Map<String, String> expected) implements ResponseExpectation {}

        record Satisfies(Consumer<TestResponse> check) implements ResponseExpectation {}
    }

    public record ExpectedSpan(String name, Map<String, String> attributes, List<ExpectedSpan> children) {}

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
            var appended = specification.appended();
            var eventsBefore = appended.size();
            var metricsBefore = specification.metricCounts();

            var response = specification.run(step, traceIds);

            var outcome = new Outcome(
                    response,
                    published.subList(messagesBefore, published.size()).stream()
                            .filter(envelope -> specification.isAbout(envelope.message()))
                            .toList(),
                    appended.subList(eventsBefore, appended.size()).stream()
                            .filter(envelope -> specification.isAbout(envelope.message()))
                            .toList(),
                    () -> specification.finishedSpans().stream()
                            .filter(span -> traceIds.contains(span.getTraceId()))
                            .toList(),
                    (name, tags) -> specification.metricIncrease(metricsBefore, name, tags));

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

    public static TestStep send(Object... commands) {
        return new Publish(List.of(commands));
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
        return outcome -> assertExactly("published", List.of(messages), outcome.published());
    }

    public static Expectation expectNothingPublished() {
        return expectPublished();
    }

    public static Expectation expectAppended(Object... events) {
        return outcome -> assertExactly("appended", List.of(events), outcome.appended());
    }

    public static Expectation expectNothingAppended() {
        return expectAppended();
    }

    /**
     * Checks what caused what, with the ids that the messages and events really carry: every message in the tree has
     * the correlation id of its root, and each child's causation id is the id of its parent. A command or an event that
     * the application published is {@link #sent} or {@link #published}, an event it stored is {@link #appended}.
     */
    public static Expectation expectCausation(Caused... roots) {
        return outcome -> {
            for (var root : roots) {
                var envelope = findIn(outcome, root, null);
                verifyCausedBy(outcome, root, envelope, envelope.metadata().correlationId());
            }
        };
    }

    public static Caused sent(Object command, Caused... caused) {
        return new Caused(Caused.Where.Published, command, List.of(caused));
    }

    public static Caused published(Object event, Caused... caused) {
        return new Caused(Caused.Where.Published, event, List.of(caused));
    }

    public static Caused appended(Object event, Caused... caused) {
        return new Caused(Caused.Where.Appended, event, List.of(caused));
    }

    public record Caused(Where where, Object message, List<Caused> caused) {
        public enum Where {
            Published,
            Appended
        }
    }

    private static void verifyCausedBy(
            Outcome outcome, Caused expected, MessageEnvelope<Object> cause, String correlationId) {
        assertThat(cause.metadata().correlationId())
                .as("Correlation id of %s", cause.message())
                .isEqualTo(correlationId);

        for (var child : expected.caused()) {
            var envelope = findIn(outcome, child, cause.metadata().messageId());
            verifyCausedBy(outcome, child, envelope, correlationId);
        }
    }

    private static MessageEnvelope<Object> findIn(Outcome outcome, Caused expected, @Nullable String causationId) {
        var recorded = expected.where() == Caused.Where.Published ? outcome.published() : outcome.appended();
        var matching = recorded.stream()
                .filter(envelope -> isEqual(envelope.message(), expected.message()))
                .toList();

        assertThat(matching)
                .as(
                        "%s %s, but %s:%n%s",
                        expected.where(), expected.message(), expected.where(), describeEnvelopes(recorded))
                .isNotEmpty();

        if (causationId == null) return matching.getFirst();

        return matching.stream()
                .filter(envelope -> causationId.equals(envelope.metadata().causationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("%s %s caused by message %s, but it was caused by %s"
                        .formatted(
                                expected.where(),
                                expected.message(),
                                causationId,
                                matching.stream()
                                        .map(envelope -> envelope.metadata().causationId())
                                        .toList())));
    }

    private static void assertExactly(String what, List<Object> expected, List<MessageEnvelope<Object>> recorded) {
        var actual = recorded.stream().map(MessageEnvelope::message).toList();

        assertThat(actual)
                .as("%s", """
                        Expected these messages %s:
                        %s
                        Messages %s:
                        %s""".formatted(what, Transcript.numbered(expected), what, Transcript.numbered(actual)))
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    private static boolean isEqual(Object actual, Object expected) {
        return new RecursiveComparisonDifferenceCalculator()
                .determineDifferences(actual, expected, new RecursiveComparisonConfiguration())
                .isEmpty();
    }

    private static String describeEnvelopes(List<MessageEnvelope<Object>> recorded) {
        return recorded.stream()
                .map(envelope -> "  %s (id %s, correlation %s, causation %s)"
                        .formatted(
                                envelope.message(),
                                envelope.metadata().messageId(),
                                envelope.metadata().correlationId(),
                                envelope.metadata().causationId()))
                .collect(Collectors.joining("\n"));
    }

    public static Expectation expectTrace(ExpectedSpan root) {
        return outcome -> {
            var mismatch = mismatchOf(root, outcome.spans().get());

            assertThat(mismatch)
                    .as(
                            "%s%nFinished spans:%n%s",
                            mismatch.orElse(""), describe(outcome.spans().get()))
                    .isEmpty();
        };
    }

    public static ExpectedSpan span(String name, ExpectedSpan... children) {
        return new ExpectedSpan(name, Map.of(), List.of(children));
    }

    public static ExpectedSpan span(String name, Map<String, String> attributes, ExpectedSpan... children) {
        return new ExpectedSpan(name, attributes, List.of(children));
    }

    public static Expectation expectCorrelatedTrace() {
        return outcome -> {
            var mismatch = correlationMismatchOf(outcome.spans().get());

            assertThat(mismatch)
                    .as(
                            "%s%nFinished spans:%n%s",
                            mismatch.orElse(""), describe(outcome.spans().get()))
                    .isEmpty();
        };
    }

    public static Expectation expectMetric(String name, Map<String, String> tags, double increase) {
        return outcome -> assertThat(outcome.metricIncrease().apply(name, tags))
                .as("Increase of metric %s%s", name, tags)
                .isEqualTo(increase);
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

    public static ResponseExpectation htmlContaining(String... fragments) {
        return satisfying(response -> assertThat(htmlOf(response)).contains(fragments));
    }

    public static ResponseExpectation htmlNotContaining(String... fragments) {
        return satisfying(response -> assertThat(htmlOf(response)).doesNotContain(fragments));
    }

    public static ResponseExpectation redirectsTo(String path) {
        return satisfying(response -> {
            var location = response.headers().getFirst(HttpHeaders.LOCATION);

            assertThat(location).as("Location").isNotNull();
            assertThat(URI.create(location).getRawPath().split(";")[0])
                    .as("Path of Location %s", location)
                    .isEqualTo(path);
        });
    }

    private static String htmlOf(TestResponse response) {
        assertThat(response.headers().getContentType())
                .as("Content type")
                .matches(
                        contentType -> contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML),
                        "is HTML");

        return response.body();
    }

    public static String weakETag(long version) {
        return new ETag(String.valueOf(version), true).formattedTag();
    }

    private @Nullable TestResponse run(TestStep step, Set<String> traceIds) {
        var response = runOnly(step, traceIds);
        whenCaughtUp();
        return response;
    }

    // Store processors may still be publishing when a message is recorded, and bus handlers may still be appending, so
    // each round waits for the store, then the bus, then the store again, until a round publishes nothing new.
    private void whenCaughtUp() {
        var deadline = System.nanoTime() + catchUpTimeout.toNanos();
        var published = published();
        int seen;
        do {
            assertThat(System.nanoTime())
                    .as("The application kept publishing messages for longer than %s", catchUpTimeout)
                    .isLessThan(deadline);
            seen = published.size();
            if (eventStore != null) eventStore.whenCaughtUp(remainingUntil(deadline));
            if (messageBus != null) messageBus.whenCaughtUp(remainingUntil(deadline));
            if (eventStore != null) eventStore.whenCaughtUp(remainingUntil(deadline));
        } while (published.size() != seen);
    }

    private static Duration remainingUntil(long deadline) {
        return Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
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

    private List<MessageEnvelope<Object>> published() {
        if (messageBus == null) return List.of();

        return publishedOn.computeIfAbsent(messageBus, bus -> {
            var recorded = new CopyOnWriteArrayList<MessageEnvelope<Object>>();
            bus.use(recorded::add);
            return recorded;
        });
    }

    private List<MessageEnvelope<Object>> appended() {
        return eventStore instanceof RecordingEventStore recording ? recording.appended() : List.of();
    }

    private List<SpanData> finishedSpans() {
        return spans == null ? List.of() : spans.getFinishedSpanItems();
    }

    private static Optional<String> mismatchOf(ExpectedSpan expected, List<SpanData> spans) {
        var named = spans.stream()
                .filter(span -> span.getName().equals(expected.name()))
                .toList();

        if (named.isEmpty()) return Optional.of("No span '%s'".formatted(expected.name()));

        var candidates = named.stream().filter(span -> matches(span, expected)).toList();

        if (candidates.isEmpty())
            return Optional.of("No span '%s' with attributes %s, the spans of that name have %s"
                    .formatted(
                            expected.name(),
                            expected.attributes(),
                            named.stream()
                                    .map(ApiE2ESpecification::attributesOf)
                                    .toList()));

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
            var named = spans.stream()
                    .filter(span -> span.getName().equals(child.name()) && isBelow(span, parent, spans))
                    .toList();

            if (named.isEmpty()) return Optional.of("No span '%s' below '%s'".formatted(child.name(), expected.name()));

            var candidates = named.stream().filter(span -> matches(span, child)).toList();

            if (candidates.isEmpty())
                return Optional.of("No span '%s' with attributes %s below '%s', the spans of that name have %s"
                        .formatted(
                                child.name(),
                                child.attributes(),
                                expected.name(),
                                named.stream()
                                        .map(ApiE2ESpecification::attributesOf)
                                        .toList()));

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

    private static boolean matches(SpanData span, ExpectedSpan expected) {
        return attributesOf(span).entrySet().containsAll(expected.attributes().entrySet());
    }

    private static Map<String, String> attributesOf(SpanData span) {
        var attributes = new TreeMap<String, String>();
        span.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), String.valueOf(value)));
        return attributes;
    }

    private static Optional<String> correlationMismatchOf(List<SpanData> spans) {
        var correlated = spans.stream()
                .filter(span -> attributesOf(span).containsKey(CORRELATION_ID))
                .toList();

        if (correlated.isEmpty()) return Optional.of("No span carries the attribute " + CORRELATION_ID);

        var traces = correlated.stream().collect(Collectors.groupingBy(SpanData::getTraceId));

        for (var trace : traces.values()) {
            var correlationId = trace.stream()
                    .collect(Collectors.groupingBy(
                            span -> attributesOf(span).get(CORRELATION_ID), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .max(Map.Entry.comparingByValue())
                    .orElseThrow()
                    .getKey();

            for (var span : trace) {
                var spanCorrelationId = attributesOf(span).get(CORRELATION_ID);
                if (!spanCorrelationId.equals(correlationId))
                    return Optional.of("Span '%s' carries correlation '%s', but its trace carries '%s'"
                            .formatted(span.getName(), spanCorrelationId, correlationId));
            }
        }

        var handlersOf = spans.stream()
                .filter(span -> attributesOf(span).containsKey(MESSAGE_ID))
                .collect(Collectors.groupingBy(span -> attributesOf(span).get(MESSAGE_ID)));

        for (var span : spans) {
            var causationId = attributesOf(span).get(CAUSATION_ID);
            if (causationId == null || causationId.equals(attributesOf(span).get(CORRELATION_ID))) continue;

            var handlers = handlersOf.getOrDefault(causationId, List.of());

            if (handlers.isEmpty())
                return Optional.of("Span '%s' was caused by message %s, but no span handled that message"
                        .formatted(span.getName(), causationId));

            if (handlers.stream().noneMatch(handler -> handler == span || isBelow(span, handler, spans)))
                return Optional.of("Span '%s' was caused by message %s, but is not below '%s' that handled it"
                        .formatted(
                                span.getName(), causationId, handlers.getFirst().getName()));
        }
        return Optional.empty();
    }

    private Map<Meter.Id, Double> metricCounts() {
        if (meters == null) return Map.of();

        return meters.getMeters().stream().collect(Collectors.toMap(Meter::getId, ApiE2ESpecification::countOf));
    }

    private double metricIncrease(Map<Meter.Id, Double> before, String name, Map<String, String> tags) {
        assertThat(meters).as("Checking metrics needs a meter registry").isNotNull();

        return meters.getMeters().stream()
                .filter(meter -> meter.getId().getName().equals(name))
                .filter(meter -> tags.entrySet().stream()
                        .allMatch(tag -> tag.getValue().equals(meter.getId().getTag(tag.getKey()))))
                .mapToDouble(meter -> countOf(meter) - before.getOrDefault(meter.getId(), 0.0))
                .sum();
    }

    private static double countOf(Meter meter) {
        return switch (meter) {
            case Counter counter -> counter.count();
            case Timer timer -> timer.count();
            case FunctionCounter counter -> counter.count();
            default -> 0;
        };
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
            lines.add("  %s (trace %s, span %s, parent %s) %s"
                    .formatted(
                            span.getName(),
                            span.getTraceId(),
                            span.getSpanId(),
                            span.getParentSpanId(),
                            attributesOf(span)));
        }
        return String.join("\n", lines);
    }

    private static String randomHex(int bytes) {
        var random = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(random);
        return HexFormat.of().formatHex(random);
    }
}
