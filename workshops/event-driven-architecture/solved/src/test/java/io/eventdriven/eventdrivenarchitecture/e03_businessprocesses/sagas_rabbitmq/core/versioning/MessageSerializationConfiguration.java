package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
public class MessageSerializationConfiguration {

  @Bean
  public MessageMappingRegistry messageMappingRegistry(
    List<MessageMapping> allMappings,
    JsonMapper jsonMapper
  ) {
    return new MessageMappingRegistry(allMappings, jsonMapper);
  }

  @Bean
  public MessageTransformations messageTransformations(MessageMappingRegistry registry) {
    return registry.getMessageTransformations();
  }
}
