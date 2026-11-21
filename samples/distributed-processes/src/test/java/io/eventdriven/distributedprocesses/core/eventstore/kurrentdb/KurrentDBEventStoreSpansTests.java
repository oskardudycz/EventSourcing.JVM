package io.eventdriven.distributedprocesses.core.eventstore.kurrentdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.distributedprocesses.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.testing.ProjectionsDatabase;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestTracing;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class KurrentDBEventStoreSpansTests {
    private record Opened(String cartId) {}

    private final TestTracing tracing = new TestTracing();
    private final KurrentDBEventStore eventStore = KurrentDBEventStore.connectingTo(
            TestDatabases.kurrentDBConnectionString(),
            tracing.messageContext(),
            TestMessages.serializerTrustingPackageOf(KurrentDBEventStoreSpansTests.class),
            tracing.observations(),
            ProjectionsDatabase.H2.bean(SubscriptionCheckpoints.class),
            RetryBackOff.exponential(Duration.ofMillis(100)));
    private final String streamId = "spans-" + UUID.randomUUID();

    @AfterEach
    void closeStore() {
        eventStore.close();
    }

    @Test
    public void anAppendShowsAsAStreamsAppendSpanInsideTheCallersSpan() {
        var step = tracing.tracer().nextSpan().name("step-" + streamId).start();

        try (var _ = tracing.tracer().withSpan(step)) {
            eventStore.append(streamId, new Opened("cart"));
        } finally {
            step.end();
        }

        var append = appendSpansOf(streamId).getFirst();
        assertThat(append.getParentSpanId()).isEqualTo(step.context().spanId());
        assertThat(append.getTraceId()).isEqualTo(step.context().traceId());
        assertThat(append.getAttributes().get(AttributeKey.stringKey("db.system")))
                .isEqualTo("kurrentdb");
        assertThat(append.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    }

    @Test
    public void aVersionConflictMarksTheStreamsAppendSpanAsAnError() {
        eventStore.append(streamId, new Opened("cart"));

        eventStore.append(streamId, ExpectedStreamVersion.noStream(), new Opened("cart"));

        assertThat(appendSpansOf(streamId))
                .anySatisfy(span -> assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR));
    }

    private List<SpanData> appendSpansOf(String streamId) {
        return tracing.finishedSpans().stream()
                .filter(span -> span.getName().equals("streams.append"))
                .filter(span ->
                        streamId.equals(span.getAttributes().get(AttributeKey.stringKey("db.kurrentdb.stream"))))
                .toList();
    }
}
