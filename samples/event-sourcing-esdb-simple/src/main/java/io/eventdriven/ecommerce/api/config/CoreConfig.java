package io.eventdriven.ecommerce.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.ecommerce.core.events.EventBus;
import io.eventdriven.ecommerce.core.events.IEventBus;
import io.eventdriven.ecommerce.core.scopes.ServiceScope;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {
  @Bean
  public ObjectMapper defaultJSONMapper() {
    return EventSerializer.mapper;
  }

  @Bean
  public ServiceScope serviceScope() {
    return new ServiceScope();
  }

  @Bean
  public IEventBus eventBus(ServiceScope serviceScope) {
    return new EventBus(serviceScope);
  }
}
