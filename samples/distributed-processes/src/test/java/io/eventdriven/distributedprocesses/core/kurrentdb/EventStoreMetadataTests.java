package io.eventdriven.distributedprocesses.core.kurrentdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.testing.TestTracing;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

public abstract class EventStoreMetadataTests {
    protected final TestTracing tracing = new TestTracing();
    protected final MessageContext context = tracing.messageContext();

    protected final String streamId = "ShoppingCart-%s".formatted(UUID.randomUUID());
    protected final String otherStreamId = "ShoppingCart-%s".formatted(UUID.randomUUID());

    protected abstract EventStore eventStore();

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
    public void deliveredAndReadEventsHaveTheSameMessageId() {
        var delivered = new CopyOnWriteArrayList<EventEnvelope<Object>>();
        eventStore().subscribe("Opened", List.of(Opened.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) delivered.add(envelope);
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

        assertThat(metadata).extracting(EventMetadata::streamId).containsOnly(streamId);
        assertThat(metadata).extracting(EventMetadata::streamPosition).containsExactly(0L, 1L);
        assertThat(metadata).extracting(EventMetadata::messageId).doesNotHaveDuplicates();
        assertThat(metadata).extracting(EventMetadata::correlationId).doesNotContainNull();
    }

    @Test
    public void aStepWithNothingToInheritGeneratesTheCorrelationId() {
        eventStore().append(streamId, new Opened("cart"), new Added("cart", "t-shirt"));

        var metadata = readMetadata(streamId);

        assertThat(metadata.getFirst().correlationId()).isNotBlank();
        assertThat(metadata)
                .extracting(EventMetadata::correlationId)
                .containsOnly(metadata.getFirst().correlationId());
        assertThat(metadata)
                .extracting(EventMetadata::causationId)
                .containsOnly(metadata.getFirst().correlationId());
    }

    @Test
    public void aHandledMessagePassesItsCorrelationIdOnAndBecomesTheCause() {
        var received = new CopyOnWriteArrayList<EventEnvelope<Object>>();
        eventStore().subscribe("Opened", List.of(Opened.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;
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

    protected List<EventMetadata> readMetadata(String streamId) {
        assertThat(eventStore().read(streamId)).isInstanceOf(EventStore.ReadResult.Success.class);

        return ((EventStore.ReadResult.Success) eventStore().read(streamId))
                .events().stream().map(EventEnvelope::metadata).toList();
    }

    record Opened(String cartId) {}

    record Added(String cartId, String productId) {}
}
