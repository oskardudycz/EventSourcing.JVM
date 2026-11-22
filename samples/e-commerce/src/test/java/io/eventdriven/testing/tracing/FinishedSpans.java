package io.eventdriven.testing.tracing;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Keeps every span the moment it ends, so a test can check spans right after the work that made them, with no export
 * delay to wait for.
 */
public class FinishedSpans implements SpanProcessor {
    private final ConcurrentLinkedQueue<SpanData> spans = new ConcurrentLinkedQueue<>();

    public List<SpanData> getFinishedSpanItems() {
        return List.copyOf(spans);
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {}

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        spans.add(span.toSpanData());
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }
}
