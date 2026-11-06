package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning;

import org.apache.kafka.common.serialization.Serializer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
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

  @Bean
  public DefaultJacksonJavaTypeMapper kafkaTypeMapper(
    MessageMappingRegistry registry
  ) {
    var typeMapper = new DefaultJacksonJavaTypeMapper();
    // See for reasoning: https://www.baeldung.com/spring-kafka
    typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);
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
  public JacksonJsonSerializer<Object> kafkaJsonSerializer(
    DefaultJacksonJavaTypeMapper typeMapper,
    JsonMapper jsonMapper
  ) {
    var serializer = new JacksonJsonSerializer<>(jsonMapper);
    serializer.setTypeMapper(typeMapper);
    return serializer;
  }

  @Bean
  public JacksonJsonDeserializer<Object> kafkaJsonDeserializer(
    DefaultJacksonJavaTypeMapper typeMapper,
    JsonMapper jsonMapper
  ) {
    var deserializer = new JacksonJsonDeserializer<>(jsonMapper);
    deserializer.setTypeMapper(typeMapper);
    deserializer.setRemoveTypeHeaders(false);
    return deserializer;
  }

  @Bean
  public DefaultKafkaProducerFactoryCustomizer producerFactoryCustomizer(
    JacksonJsonSerializer<Object> kafkaJsonSerializer
  ) {
    return factory -> {
      factory.setValueSerializer((Serializer) kafkaJsonSerializer);
    };
  }
}
