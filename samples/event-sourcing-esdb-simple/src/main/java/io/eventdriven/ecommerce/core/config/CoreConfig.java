package io.eventdriven.ecommerce.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.ecommerce.core.events.EventBus;
import io.eventdriven.ecommerce.core.events.EventForwarder;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {
  @Bean
  public ObjectMapper defaultJSONMapper() {
    return EventSerializer.mapper;
  }

  @Bean
  public EventBus eventBus(ApplicationEventPublisher applicationEventPublisher) {
    return new EventForwarder(applicationEventPublisher);
  }
}
