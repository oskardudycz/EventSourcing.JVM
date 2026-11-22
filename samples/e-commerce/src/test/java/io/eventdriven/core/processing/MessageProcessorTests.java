package io.eventdriven.core.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.testing.messaging.TestMessages;
import io.eventdriven.testing.tracing.TestTracing;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MessageProcessorTests {
    private static final AttributeKey<String> TYPE = AttributeKey.stringKey("eventdriven.processor.type");
    private static final AttributeKey<String> NAME = AttributeKey.stringKey("eventdriven.processor.name");

    private final TestTracing tracing = new TestTracing();
    private final InMemoryEventStore eventStore = new InMemoryEventStore(
            tracing.messageContext(), TestMessages.serializerTrustingPackageOf(MessageProcessorTests.class));

    record ItemAdded(String cartId) {}

    record CartClosed(String cartId) {}

    static class CartForwarder implements Forwarder {
        @Override
        public List<Class<?>> canHandle() {
            return List.of(ItemAdded.class, CartClosed.class);
        }

        @Override
        public void handle(MessageEnvelope<Object> envelope) {}
    }

    static class CartClosedEmail implements Reactor {
        @Override
        public List<Class<?>> canHandle() {
            return List.of(CartClosed.class);
        }

        @Override
        public void handle(MessageEnvelope<Object> envelope) {}
    }

    static class FailingReactor implements Reactor {
        @Override
        public List<Class<?>> canHandle() {
            return List.of(ItemAdded.class);
        }

        @Override
        public void handle(MessageEnvelope<Object> envelope) {
            throw new IllegalStateException("The outside service is unreachable");
        }
    }

    @Test
    public void eachProcessorThatHandlesAnEventGetsItsOwnSpanBelowTheHandlingSpanInOrder() {
        eventStore.subscribe("carts", new CartForwarder(), new CartClosedEmail());

        eventStore.append("Cart-1", new CartClosed("1"));
        eventStore.whenCaughtUp();

        assertThat(childrenOf("handle CartClosed"))
                .extracting(
                        SpanData::getName,
                        span -> span.getAttributes().get(TYPE),
                        span -> span.getAttributes().get(NAME))
                .containsExactly(
                        tuple("forward CartForwarder", "forwarder", "CartForwarder"),
                        tuple("react CartClosedEmail", "reactor", "CartClosedEmail"));
        assertThat(tracing.meters()
                        .get("eventdriven.processor.handle")
                        .tags("eventdriven.processor.type", "reactor", "eventdriven.processor.name", "CartClosedEmail")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    public void aProcessorGetsNoSpanForAnEventItDoesNotHandle() {
        eventStore.subscribe("carts", new CartForwarder(), new CartClosedEmail());

        eventStore.append("Cart-1", new ItemAdded("1"));
        eventStore.whenCaughtUp();

        assertThat(childrenOf("handle ItemAdded"))
                .extracting(SpanData::getName)
                .containsExactly("forward CartForwarder");
    }

    @Test
    public void aFailingProcessorMarksItsOwnSpanAsAnError() {
        eventStore.subscribe("carts", new FailingReactor());

        eventStore.append("Cart-1", new ItemAdded("1"));

        assertThat(childrenOf("handle ItemAdded")).singleElement().satisfies(span -> {
            assertThat(span.getName()).isEqualTo("react FailingReactor");
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        });
    }

    private List<SpanData> childrenOf(String parentName) {
        var spans = tracing.finishedSpans();
        var parent = spans.stream()
                .filter(span -> span.getName().equals(parentName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No span named " + parentName + " in " + spans));

        return spans.stream()
                .filter(span -> span.getParentSpanId().equals(parent.getSpanId()))
                .sorted((first, second) -> Long.compare(first.getStartEpochNanos(), second.getStartEpochNanos()))
                .toList();
    }
}
