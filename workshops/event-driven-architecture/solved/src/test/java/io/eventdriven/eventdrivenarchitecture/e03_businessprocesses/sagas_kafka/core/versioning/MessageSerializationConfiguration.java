package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning;

import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.VersionedMessageConverter;
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
    MessageMappingRegistry registry,
    // Kids don't try this at home with "*"! See https://github.com/frohoff/ysoserial
    @Value("${app.messaging.trusted-packages:*}") String[] trustedPackages
  ) {
    var typeMapper = new DefaultJacksonJavaTypeMapper();
    // See for reasoning: https://www.baeldung.com/spring-kafka
    typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);
    typeMapper.setIdClassMapping(registry.getTypeMappings());
    typeMapper.addTrustedPackages(trustedPackages);

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
  public VersionedMessageConverter versionedMessageConverter(
    JacksonJsonDeserializer<Object> kafkaJsonDeserializer,
    JsonMapper jsonMapper,
    MessageTransformations messageTransformations
  ) {
    return new VersionedMessageConverter(kafkaJsonDeserializer, jsonMapper, messageTransformations);
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
