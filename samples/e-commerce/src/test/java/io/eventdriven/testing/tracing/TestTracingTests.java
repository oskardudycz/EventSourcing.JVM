package io.eventdriven.testing.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TestTracingTests {
    @Test
    public void eachInstanceSeesOnlyItsOwnSpans() {
        var first = new TestTracing();
        var second = new TestTracing();

        first.tracer().nextSpan().name("first-span").start().end();
        second.tracer().nextSpan().name("second-span").start().end();

        assertThat(first.finishedSpans()).extracting(span -> span.getName()).containsExactly("first-span");
        assertThat(second.finishedSpans()).extracting(span -> span.getName()).containsExactly("second-span");
    }
}
