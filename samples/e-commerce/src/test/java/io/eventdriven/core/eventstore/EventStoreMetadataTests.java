package io.eventdriven.core.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.testing.messaging.TestMessages;
import io.eventdriven.testing.tracing.TestTracing;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

public abstract class EventStoreMetadataTests {
    protected final TestTracing tracing = new TestTracing();
    protected final MessageContext context = tracing.messageContext();

    protected final String streamId = "ShoppingCart-%s".formatted(UUID.randomUUID());
    protected final String otherStreamId = "ShoppingCart-%s".formatted(UUID.randomUUID());

    protected abstract EventStore eventStore();

    protected static MessageSerializer serializer() {
        return TestMessages.serializerTrustingPackageOf(EventStoreMetadataTests.class);
    }

    @Test
    public void appendedEventKeepsItsFiveValues() {
        var testSpan = tracing.tracer().nextSpan().name("test").start();

        try (var ignored = tracing.tracer().withSpan(testSpan);
                var scope = context.handling("m-1", "c-1")) {
            eventStore().append(streamId, new Opened("cart"));
        } finally {
            testSpan.end();
        }

        var metadata = readMetadata(streamId).getFirst();
        assertThat(UUID.fromString(metadata.messageId())).isNotNull();
        assertThat(metadata.correlationId()).isEqualTo("c-1");
        assertThat(metadata.causationId()).isEqualTo("m-1");
        assertThat(metadata.traceId()).isEqualTo(testSpan.context().traceId());
        assertThat(metadata.spanId()).isEqualTo(testSpan.context().spanId());
    }

    @Test
    public void aSubscriberHandlesTheEventInTheTraceOfItsAppend() {
        eventStore().subscribe("trace-" + UUID.randomUUID(), List.of(Opened.class), envelope -> {});
        var testSpan = tracing.tracer().nextSpan().name("test").start();

        try (var ignored = tracing.tracer().withSpan(testSpan)) {
            eventStore().append(streamId, new Opened("cart"));
        } finally {
            testSpan.end();
        }

        var stored = readMetadata(streamId).getFirst();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(tracing.finishedSpans())
                        .anyMatch(span -> span.getName().equals("handle Opened")
                                && span.getTraceId().equals(stored.traceId())
                                && span.getParentSpanId().equals(stored.spanId())));
    }

    @Test
    public void theSubscribersSpanCarriesTheEventsIdAndTheProcessItBelongsTo() {
        eventStore().subscribe("ids-" + UUID.randomUUID(), List.of(Opened.class), envelope -> {});

        try (var scope = context.handling("m-1", "c-1")) {
            eventStore().append(streamId, new Opened("cart"));
        }

        var stored = readMetadata(streamId).getFirst();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(tracing.finishedSpans())
                        .filteredOn(span -> span.getName().equals("handle Opened"))
                        .filteredOn(span ->
                                stored.messageId().equals(attributesOf(span).get("messaging.message.id")))
                        .singleElement()
                        .satisfies(span -> assertThat(attributesOf(span))
                                .containsEntry("messaging.message.correlation_id", "c-1")
                                .containsEntry("messaging.message.causation_id", "m-1")));
    }

    @Test
    public void deliveredAndReadEventsHaveTheSameMessageId() {
        var delivered = new CopyOnWriteArrayList<MessageEnvelope<Object>>();
        eventStore().subscribe("Opened", List.of(Opened.class), envelope -> {
            if (envelope.metadata().stream().streamId().equals(streamId)) delivered.add(envelope);
        });

        eventStore().append(streamId, new Opened("cart"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !delivered.isEmpty());
        assertThat(delivered.getFirst().metadata().messageId())
                .isEqualTo(readMetadata(streamId).getFirst().messageId());
    }

    @Test
    public void readReturnsEachEventsMetadata() {
        eventStore().append(streamId, new Opened("cart"), new Added("cart", "t-shirt"));

        var metadata = readMetadata(streamId);

        assertThat(metadata).extracting(each -> each.stream().streamId()).containsOnly(streamId);
        assertThat(metadata).extracting(each -> each.stream().streamPosition()).containsExactly(0L, 1L);
        assertThat(metadata).extracting(MessageMetadata::messageId).doesNotHaveDuplicates();
        assertThat(metadata).extracting(MessageMetadata::correlationId).doesNotContainNull();
    }

    @Test
    public void aStepWithNothingToInheritGeneratesTheCorrelationId() {
        eventStore().append(streamId, new Opened("cart"), new Added("cart", "t-shirt"));

        var metadata = readMetadata(streamId);

        assertThat(metadata.getFirst().correlationId()).isNotBlank();
        assertThat(metadata)
                .extracting(MessageMetadata::correlationId)
                .containsOnly(metadata.getFirst().correlationId());
        assertThat(metadata)
                .extracting(MessageMetadata::causationId)
                .containsOnly(metadata.getFirst().correlationId());
    }

    @Test
    public void aHandledMessagePassesItsCorrelationIdOnAndBecomesTheCause() {
        var received = new CopyOnWriteArrayList<MessageEnvelope<Object>>();
        eventStore().subscribe("Opened", List.of(Opened.class), envelope -> {
            if (!envelope.metadata().stream().streamId().equals(streamId)) return;
            received.add(envelope);
            eventStore().append(otherStreamId, new Added("cart", "caused"));
        });

        eventStore().append(streamId, new Opened("cart"));

        await().atMost(Duration.ofSeconds(10)).until(() -> !received.isEmpty());
        var handled = received.getFirst().metadata();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(readMetadata(otherStreamId)).hasSize(1));
        var caused = readMetadata(otherStreamId).getFirst();
        assertThat(caused.correlationId()).isEqualTo(handled.correlationId());
        assertThat(caused.causationId()).isEqualTo(handled.messageId());
    }

    @Test
    public void aMessagePublishedAgainWhileHandlingARedeliveredEventKeepsItsId() {
        var publishedIds = new CopyOnWriteArrayList<String>();
        eventStore().subscribe("redelivered-" + UUID.randomUUID(), List.of(Opened.class), envelope -> {
            if (!envelope.metadata().stream().streamId().equals(streamId)) return;

            publishedIds.add(context.inheritForPublished("test.Confirm", 0).messageId());

            if (publishedIds.size() == 1) throw new IllegalStateException("The process crashed after publishing");
        });

        eventStore().append(streamId, new Opened("cart"));
        eventStore().append(otherStreamId, new Opened("later"));

        await().atMost(Duration.ofSeconds(30)).until(() -> publishedIds.size() >= 2);
        assertThat(publishedIds.get(1))
                .isEqualTo(publishedIds.get(0))
                .isNotEqualTo(readMetadata(streamId).getFirst().messageId());
    }

    @Test
    public void anAppendRecordsItsStreamBatchAndCorrelationOnASpanInTheCallersTrace() {
        var testSpan = tracing.tracer().nextSpan().name("test").start();

        try (var ignored = tracing.tracer().withSpan(testSpan);
                var scope = context.handling("m-1", "c-1")) {
            eventStore().append(streamId, new Opened("cart"), new Added("cart", "shoes"));
        } finally {
            testSpan.end();
        }

        assertThat(storeSpansOf("eventStore.appendToStream").getLast().getParentSpanId())
                .isEqualTo(testSpan.context().spanId());
        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.eventstore.append")
                .that()
                .hasContextualNameEqualTo("eventStore.appendToStream")
                .hasLowCardinalityKeyValue("eventdriven.eventstore.operation", "appendToStream")
                .hasLowCardinalityKeyValue("eventdriven.eventstore.append.status", "success")
                .hasHighCardinalityKeyValue("eventdriven.stream.name", streamId)
                .hasHighCardinalityKeyValue("eventdriven.eventstore.append.batch_size", "2")
                .hasHighCardinalityKeyValue("eventdriven.stream.version.after", "1")
                .hasHighCardinalityKeyValue("messaging.message.correlation_id", "c-1")
                .hasHighCardinalityKeyValue("messaging.message.causation_id", "m-1");
    }

    @Test
    public void anAppendThatConflictsRecordsTheConflictOnItsSpan() {
        eventStore().append(streamId, new Opened("cart"));
        tracing.observations().clear();

        eventStore().append(streamId, ExpectedStreamVersion.noStream(), new Opened("cart"));

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.eventstore.append")
                .that()
                .hasLowCardinalityKeyValue("eventdriven.eventstore.append.status", "conflict");
    }

    @Test
    public void aReadRecordsTheEventsItFoundOnASpan() {
        eventStore().append(streamId, new Opened("cart"), new Added("cart", "shoes"), new Added("cart", "shirt"));
        tracing.observations().clear();

        eventStore().read(streamId);

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.eventstore.read")
                .that()
                .hasContextualNameEqualTo("eventStore.readStream")
                .hasLowCardinalityKeyValue("eventdriven.eventstore.operation", "readStream")
                .hasLowCardinalityKeyValue("eventdriven.eventstore.read.status", "success")
                .hasHighCardinalityKeyValue("eventdriven.stream.name", streamId)
                .hasHighCardinalityKeyValue("eventdriven.eventstore.read.event_count", "3")
                .hasHighCardinalityKeyValue("eventdriven.eventstore.read.event_types", "Opened,Added");
    }

    @Test
    public void aReadOfAMissingStreamRecordsThatItFoundNoEvents() {
        eventStore().read(streamId);

        assertThat(tracing.observations())
                .hasObservationWithNameEqualTo("eventdriven.eventstore.read")
                .that()
                .hasLowCardinalityKeyValue("eventdriven.eventstore.read.status", "success")
                .hasHighCardinalityKeyValue("eventdriven.eventstore.read.event_count", "0");
    }

    @Test
    public void anAppendCountsEachEventByItsType() {
        eventStore().append(streamId, new Opened("cart"), new Added("cart", "shoes"), new Added("cart", "shirt"));

        assertThat(appendedCountOf("Opened")).isEqualTo(1);
        assertThat(appendedCountOf("Added")).isEqualTo(2);
    }

    @Test
    public void subscribingToAnEventTypeStartsItsAppendedCountAtZero() {
        eventStore().subscribe("counted-" + UUID.randomUUID(), List.of(Added.class), envelope -> {});

        assertThat(tracing.meters()
                        .find("eventdriven.event.appending.count")
                        .tag("eventdriven.event.type", "Added")
                        .counter())
                .isNotNull();
    }

    private double appendedCountOf(String eventType) {
        var counter = tracing.meters()
                .find("eventdriven.event.appending.count")
                .tag("eventdriven.event.type", eventType)
                .counter();

        return counter != null ? counter.count() : 0;
    }

    private List<SpanData> storeSpansOf(String name) {
        return tracing.finishedSpans().stream()
                .filter(span -> span.getName().equals(name))
                .filter(span -> streamId.equals(attributesOf(span).get("eventdriven.stream.name")))
                .toList();
    }

    private static Map<String, String> attributesOf(SpanData span) {
        var attributes = new HashMap<String, String>();
        span.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), String.valueOf(value)));
        return attributes;
    }

    protected List<MessageMetadata> readMetadata(String streamId) {
        assertThat(eventStore().read(streamId)).isInstanceOf(EventStore.ReadResult.Success.class);

        return ((EventStore.ReadResult.Success) eventStore().read(streamId))
                .events().stream().map(MessageEnvelope::metadata).toList();
    }

    public record Opened(String cartId) {}

    public record Added(String cartId, String productId) {}
}
