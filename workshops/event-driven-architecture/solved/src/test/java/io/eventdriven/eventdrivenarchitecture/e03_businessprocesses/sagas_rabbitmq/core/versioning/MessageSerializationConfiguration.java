package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;
import org.springframework.kafka.support.mapping.Jackson2JavaTypeMapper;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.List;

@Configuration
public class MessageSerializationConfiguration {

  @Bean
  public MessageMappingRegistry messageMappingRegistry(
    List<MessageMapping> allMappings,
    ObjectMapper objectMapper
  ) {
    return new MessageMappingRegistry(allMappings, objectMapper);
  }

  @Bean
  public MessageTransformations messageTransformations(MessageMappingRegistry registry) {
    return registry.getMessageTransformations();
  }

  @Bean
  public DefaultJackson2JavaTypeMapper kafkaTypeMapper(
    MessageMappingRegistry registry
  ) {
    var typeMapper = new DefaultJackson2JavaTypeMapper();
    // See for reasoning: https://www.baeldung.com/spring-kafka
    typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);
    typeMapper.setIdClassMapping(registry.getTypeMappings());

    var trustedPackages = registry.getTrustedPackages();
    if (trustedPackages.isEmpty()) {
      // Kids don't try this at home! See https://github.com/frohoff/ysoserial
      typeMapper.addTrustedPackages("*");
    } else {
      typeMapper.addTrustedPackages(trustedPackages.toArray(String[]::new));
    }

    return typeMapper;
  }

  @Bean
  public JsonSerializer<Object> kafkaJsonSerializer(
    DefaultJackson2JavaTypeMapper typeMapper,
    ObjectMapper objectMapper
  ) {
    var serializer = new JsonSerializer<>(objectMapper);
    serializer.setTypeMapper(typeMapper);
    return serializer;
  }

  @Bean
  public JsonDeserializer<Object> kafkaJsonDeserializer(
    DefaultJackson2JavaTypeMapper typeMapper,
    ObjectMapper objectMapper
  ) {
    var deserializer = new JsonDeserializer<>(objectMapper);
    deserializer.setTypeMapper(typeMapper);
    deserializer.setRemoveTypeHeaders(false);
    return deserializer;
  }

  @Bean
  public DefaultKafkaProducerFactoryCustomizer producerFactoryCustomizer(
    JsonSerializer<Object> kafkaJsonSerializer
  ) {
    return factory -> {
      factory.setValueSerializer((Serializer) kafkaJsonSerializer);
    };
  }
}
