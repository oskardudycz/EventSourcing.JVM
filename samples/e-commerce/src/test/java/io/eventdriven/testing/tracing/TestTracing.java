package io.eventdriven.testing.tracing;

import io.eventdriven.core.messaging.MessageContext;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collections;
import java.util.List;

public final class TestTracing {
    private final InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
    private final Tracer tracer;

    public TestTracing() {
        var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                        .build())
                .build();
        var currentTraceContext = new OtelCurrentTraceContext();

        tracer = new OtelTracer(
                openTelemetry.getTracer("test-tracer"),
                currentTraceContext,
                event -> {},
                new OtelBaggageManager(
                        currentTraceContext,
                        List.of(
                                MessageContext.CORRELATION_ID,
                                MessageContext.CAUSATION_ID,
                                MessageContext.HANDLED_MESSAGE_ID),
                        Collections.emptyList()));
    }

    public static MessageContext untraced() {
        return new MessageContext(Tracer.NOOP);
    }

    public List<SpanData> finishedSpans() {
        return spanExporter.getFinishedSpanItems();
    }

    public InMemorySpanExporter spanExporter() {
        return spanExporter;
    }

    public Tracer tracer() {
        return tracer;
    }

    public ObservationRegistry observations() {
        var observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));

        return observations;
    }

    public MessageContext messageContext() {
        return new MessageContext(tracer);
    }
}
