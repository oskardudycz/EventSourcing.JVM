package io.eventdriven.testing.tracing;

import io.eventdriven.core.messaging.MessageContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Collections;
import java.util.List;

public final class TestTracing {
    private final FinishedSpans spans = new FinishedSpans();
    private final TestObservationRegistry observations = TestObservationRegistry.create();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final Tracer tracer;

    public TestTracing() {
        var openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(
                        SdkTracerProvider.builder().addSpanProcessor(spans).build())
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
        observations
                .observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(tracer))
                .observationHandler(new DefaultMeterObservationHandler(meters));
    }

    public static MessageContext untraced() {
        return new MessageContext(Tracer.NOOP, ObservationRegistry.NOOP, new SimpleMeterRegistry());
    }

    public List<SpanData> finishedSpans() {
        return spans.getFinishedSpanItems();
    }

    public FinishedSpans spans() {
        return spans;
    }

    public MeterRegistry meters() {
        return meters;
    }

    public Tracer tracer() {
        return tracer;
    }

    public TestObservationRegistry observations() {
        return observations;
    }

    public MessageContext messageContext() {
        return new MessageContext(tracer, observations(), meters);
    }
}
