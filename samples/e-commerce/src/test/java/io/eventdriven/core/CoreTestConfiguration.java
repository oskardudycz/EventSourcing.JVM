package io.eventdriven.core;

import io.eventdriven.core.messaging.MessageContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class
        })
public class CoreTestConfiguration {
    @Bean
    MessageContext messageContext(
            Tracer tracer, ObjectProvider<ObservationRegistry> observations, ObjectProvider<MeterRegistry> meters) {
        return new MessageContext(
                tracer,
                observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                meters.getIfAvailable(SimpleMeterRegistry::new));
    }
}
