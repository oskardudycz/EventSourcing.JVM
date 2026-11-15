package io.eventdriven.ecommerce.core.config;

import tools.jackson.databind.json.JsonMapper;
import io.eventdriven.ecommerce.core.events.EventBus;
import io.eventdriven.ecommerce.core.events.EventForwarder;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CoreConfig {
  @Bean
  JsonMapper defaultJSONMapper() {
    return EventSerializer.mapper;
  }

  @Bean
  EventBus eventBus(ApplicationEventPublisher applicationEventPublisher) {
    return new EventForwarder(applicationEventPublisher);
  }
}
