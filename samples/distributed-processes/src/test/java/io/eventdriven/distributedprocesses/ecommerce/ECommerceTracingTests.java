package io.eventdriven.distributedprocesses.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
@AutoConfigureTracing
@Import(InMemorySpansConfiguration.class)
public class ECommerceTracingTests {
    record Opened(String cartId) {}

    @Autowired
    private EventStore eventStore;

    @Autowired
    private Tracer tracer;

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private InMemorySpanExporter spans;

    @Test
    public void eventAppendedInsideASpanKeepsTheSpansTraceAndTheBaggageValues() {
        var streamId = "ShoppingCart-" + UUID.randomUUID();
        var span = tracer.nextSpan().name("test").start();

        try (var _ = tracer.withSpan(span);
                var _ = messageContext.handling("m-1", "c-1")) {
            eventStore.append(streamId, new Opened("cart"));
        } finally {
            span.end();
        }

        var metadata = ((EventStore.ReadResult.Success) eventStore.read(streamId))
                .events().stream().map(EventEnvelope::metadata).findFirst().orElseThrow();
        assertThat(metadata.correlationId()).isEqualTo("c-1");
        assertThat(metadata.causationId()).isEqualTo("m-1");
        assertThat(metadata.traceId()).isEqualTo(span.context().traceId());
        assertThat(metadata.spanId()).isEqualTo(span.context().spanId());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .anyMatch(finished ->
                                finished.getSpanContext().getSpanId().equals(metadata.spanId())));
    }

    @Test
    public void subscriberContinuesTheTraceOfTheStoredEvent() {
        var streamId = "ShoppingCart-" + UUID.randomUUID();
        var span = tracer.nextSpan().name("test").start();

        eventStore.subscribe("trace-continuation-" + UUID.randomUUID(), List.of(Opened.class), envelope -> {});
        try (var _ = tracer.withSpan(span)) {
            eventStore.append(streamId, new Opened("cart"));
        } finally {
            span.end();
        }

        var stored = ((EventStore.ReadResult.Success) eventStore.read(streamId))
                .events().stream().map(EventEnvelope::metadata).findFirst().orElseThrow();
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .anyMatch(finished -> finished.getName().equals("handle Opened")
                                && finished.getTraceId().equals(stored.traceId())
                                && finished.getParentSpanId().equals(stored.spanId())));
    }
}
