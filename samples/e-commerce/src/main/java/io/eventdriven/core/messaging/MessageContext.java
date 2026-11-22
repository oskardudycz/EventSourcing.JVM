package io.eventdriven.core.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public class MessageContext {
    public static final String CORRELATION_ID = "correlationId";
    public static final String CAUSATION_ID = "causationId";
    public static final String HANDLED_MESSAGE_ID = "handledMessageId";

    private final Tracer tracer;

    public MessageContext(Tracer tracer) {
        this.tracer = tracer;
    }

    public MessageValues inherit() {
        var correlationId = baggage(CORRELATION_ID).orElseGet(MessageContext::newId);
        var causationId = baggage(CAUSATION_ID).orElse(correlationId);
        var span = tracer.currentSpan();

        return new MessageValues(
                newId(),
                correlationId,
                causationId,
                span != null ? nonBlank(span.context().traceId()) : null,
                span != null ? nonBlank(span.context().spanId()) : null);
    }

    public MessageValues inheritForPublished(String typeName, int position) {
        var values = inherit();

        return baggage(HANDLED_MESSAGE_ID)
                .map(handledMessageId -> values.withMessageId(derivedId(handledMessageId, typeName, position)))
                .orElse(values);
    }

    public Scope handling(String messageId, String correlationId) {
        var handled = tracer.createBaggageInScope(HANDLED_MESSAGE_ID, messageId);
        var scope = baggageScope(correlationId, messageId);

        return () -> {
            scope.close();
            handled.close();
        };
    }

    public Scope publishing(MessageValues values) {
        return baggageScope(values.correlationId(), values.causationId());
    }

    public Scope handling(String stepName, EventMetadata metadata) {
        var step = startStep(stepName, metadata);

        return stepScope(step, metadata.messageId(), metadata.correlationId());
    }

    public Scope continuing(String stepName, EventMetadata lastEvent) {
        var handledMessageId = baggage(HANDLED_MESSAGE_ID);
        var step = startStep(stepName, lastEvent);

        return handledMessageId
                .map(messageId -> stepScope(step, messageId, lastEvent.correlationId()))
                .orElseGet(() ->
                        stepScope(step, () -> baggageScope(lastEvent.correlationId(), lastEvent.correlationId())));
    }

    public Scope consuming(String stepName, String messageId) {
        var step = tracer.nextSpan().name(stepName).start();

        return stepScope(step, messageId, baggage(CORRELATION_ID).orElseGet(MessageContext::newId));
    }

    private Scope stepScope(Span step, String messageId, String correlationId) {
        return stepScope(step, () -> handling(messageId, correlationId));
    }

    private Scope stepScope(Span step, Supplier<Scope> messageScopeFactory) {
        var stepScope = tracer.withSpan(step);
        var messageScope = messageScopeFactory.get();

        return () -> {
            messageScope.close();
            stepScope.close();
            step.end();
        };
    }

    private Scope baggageScope(String correlationId, String causationId) {
        var correlation = tracer.createBaggageInScope(CORRELATION_ID, correlationId);
        var causation = tracer.createBaggageInScope(CAUSATION_ID, causationId);

        return () -> {
            causation.close();
            correlation.close();
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private Span startStep(String stepName, EventMetadata metadata) {
        if (metadata.traceId() == null || metadata.spanId() == null)
            return tracer.nextSpan().name(stepName).start();

        var parent = tracer.traceContextBuilder()
                .traceId(metadata.traceId())
                .spanId(metadata.spanId())
                .sampled(true)
                .build();

        return tracer.spanBuilder().setParent(parent).name(stepName).start();
    }

    private Optional<String> baggage(String name) {
        var baggage = tracer.getBaggage(name);

        return baggage != null ? Optional.ofNullable(baggage.get()) : Optional.empty();
    }

    private static String derivedId(String handledMessageId, String typeName, int position) {
        var name = "%s:%s:%d".formatted(handledMessageId, typeName, position);

        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static @Nullable String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
