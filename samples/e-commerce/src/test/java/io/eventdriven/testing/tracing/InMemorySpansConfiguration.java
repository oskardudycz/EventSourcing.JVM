package io.eventdriven.testing.tracing;

import org.springframework.context.annotation.Bean;

public class InMemorySpansConfiguration {
    @Bean
    public FinishedSpans finishedSpans() {
        return new FinishedSpans();
    }
}
