package io.eventdriven.testing;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.springframework.context.annotation.Bean;

public class InMemorySpansConfiguration {
    @Bean
    public InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }
}
