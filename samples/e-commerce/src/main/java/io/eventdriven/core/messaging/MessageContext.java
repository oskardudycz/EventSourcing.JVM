package io.eventdriven.core.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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
    private static final String MESSAGE_ID_ATTRIBUTE = "messaging.message.id";
    private static final String CORRELATION_ID_ATTRIBUTE = "messaging.message.correlation_id";
    private static final String CAUSATION_ID_ATTRIBUTE = "messaging.message.causation_id";

    private final Tracer tracer;
    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    public MessageContext(Tracer tracer, ObservationRegistry observations, MeterRegistry meters) {
        this.tracer = tracer;
        this.observations = observations;
        this.meters = meters;
    }

    public MeterRegistry meters() {
        return meters;
    }

    public Observation observation(String name) {
        var observation = Observation.createNotStarted(name, observations);
        baggage(CORRELATION_ID).ifPresent(id -> observation.highCardinalityKeyValue(CORRELATION_ID_ATTRIBUTE, id));
        baggage(CAUSATION_ID).ifPresent(id -> observation.highCardinalityKeyValue(CAUSATION_ID_ATTRIBUTE, id));

        return observation;
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
        var step = startStep(stepName, metadata)
                .tag(MESSAGE_ID_ATTRIBUTE, metadata.messageId())
                .tag(CORRELATION_ID_ATTRIBUTE, metadata.correlationId())
                .tag(CAUSATION_ID_ATTRIBUTE, metadata.causationId());

        return stepScope(step, metadata.messageId(), metadata.correlationId());
    }

    public Scope continuing(String stepName, EventMetadata lastEvent) {
        var handledMessageId = baggage(HANDLED_MESSAGE_ID);
        var step = startStep(stepName, lastEvent).tag(CORRELATION_ID_ATTRIBUTE, lastEvent.correlationId());
        handledMessageId.ifPresent(messageId -> step.tag(MESSAGE_ID_ATTRIBUTE, messageId));

        return handledMessageId
                .map(messageId -> stepScope(step, messageId, lastEvent.correlationId()))
                .orElseGet(() ->
                        stepScope(step, () -> baggageScope(lastEvent.correlationId(), lastEvent.correlationId())));
    }

    public Scope consuming(String stepName, String messageId) {
        var correlationId = baggage(CORRELATION_ID).orElseGet(MessageContext::newId);
        var step = tracer.nextSpan()
                .name(stepName)
                .tag(MESSAGE_ID_ATTRIBUTE, messageId)
                .tag(CORRELATION_ID_ATTRIBUTE, correlationId)
                .tag(CAUSATION_ID_ATTRIBUTE, baggage(CAUSATION_ID).orElse(correlationId))
                .start();

        return stepScope(step, messageId, correlationId);
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
