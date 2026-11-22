package io.eventdriven.testing.eventstore;

import io.eventdriven.core.eventstore.EventStore;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;

/** Wraps the application's event store in a {@link RecordingEventStore}. */
public class RecordedEventsConfiguration {
    @Bean
    static BeanPostProcessor recordingEventStore() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof EventStore store && !(bean instanceof RecordingEventStore)
                        ? new RecordingEventStore(store)
                        : bean;
            }
        };
    }
}
