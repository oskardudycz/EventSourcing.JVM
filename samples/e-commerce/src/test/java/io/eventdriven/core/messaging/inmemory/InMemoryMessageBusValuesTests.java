package io.eventdriven.core.messaging.inmemory;

import static io.eventdriven.core.messaging.MessageContext.CAUSATION_ID;
import static io.eventdriven.core.messaging.MessageContext.CORRELATION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.MessageSource;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InMemoryMessageBusValuesTests {
    record First(String id) {}

    record Second(String id) {}

    private final TestTracing tracing = new TestTracing();
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            tracing.messageContext(),
            List.of(new MessageRoutes()
                    .routeTarget(
                            MessageRoutes.inPackage(InMemoryMessageBusValuesTests.class.getPackageName()),
                            "published")),
            List.of(new MessageChannel("published")),
            List.of(new MessageQueue("queue", "published", "#")));
    private final List<String> messageIds = new ArrayList<>();
    private final List<String> correlationIds = new ArrayList<>();

    public InMemoryMessageBusValuesTests() {
        messageBus
                .subscribe("queue", First.class, message -> observe())
                .subscribe("queue", Second.class, message -> observe());
    }

    @Test
    public void messagesPublishedWithNothingToInheritGetRandomIdsAndNewCorrelationIds() {
        messageBus.publish(List.of(new First("a")));
        messageBus.publish(List.of(new First("a")));

        assertThat(messageIds).doesNotHaveDuplicates();
        assertThat(correlationIds).doesNotHaveDuplicates();
    }

    @Test
    public void messagesPublishedWhileHandlingKeepTheCorrelationIdAndGetDerivedIds() {
        try (var _ = tracing.messageContext().handling("m-1", "c-1")) {
            messageBus.publish(List.of(new First("a"), new Second("b")));
            messageBus.publish(List.of(new First("a"), new Second("b")));
        }

        assertThat(correlationIds).containsOnly("c-1");
        assertThat(messageIds).hasSize(4).doesNotContain("m-1");
        assertThat(messageIds.get(0)).isEqualTo(messageIds.get(2)).isNotEqualTo(messageIds.get(1));
        assertThat(messageIds.get(1)).isEqualTo(messageIds.get(3));
    }

    @Test
    public void aHandledEventWithoutStoredCorrelationGivesTheSamePublishedIdsOnRedelivery() {
        var legacyEvent = new MessageMetadata(
                "m-1", "m-1", "m-1", null, null, new MessageSource.InStream("Stream-1", 0, 0, 0, Instant.EPOCH));

        for (var delivery = 0; delivery < 2; delivery++)
            try (var _ = tracing.messageContext().handling("handle First", legacyEvent)) {
                messageBus.publish(List.of(new First("a")));
            }

        assertThat(messageIds).hasSize(2).doesNotContain("m-1");
        assertThat(messageIds.get(0)).isEqualTo(messageIds.get(1));
    }

    @Test
    public void aContinuingScopeWithoutAHandledMessageGivesRandomPublishedIds() {
        var lastEvent = new MessageMetadata(
                "m-1", "c-1", "c-1", null, null, new MessageSource.InStream("Stream-1", 0, 0, 0, Instant.EPOCH));

        for (var attempt = 0; attempt < 2; attempt++)
            try (var _ = tracing.messageContext().continuing("continue", lastEvent)) {
                messageBus.publish(List.of(new First("a")));
            }

        assertThat(correlationIds).containsOnly("c-1");
        assertThat(messageIds).hasSize(2).doesNotHaveDuplicates();
    }

    private void observe() {
        messageIds.add(tracing.tracer().getBaggage(CAUSATION_ID).get());
        correlationIds.add(tracing.tracer().getBaggage(CORRELATION_ID).get());
    }
}
