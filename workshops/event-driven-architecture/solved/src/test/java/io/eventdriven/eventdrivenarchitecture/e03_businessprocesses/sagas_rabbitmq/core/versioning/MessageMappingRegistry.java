package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MessageMappingRegistry {
  private final Map<String, Class<?>> typeMappings;
  private final Set<String> trustedPackages;
  private final MessageTransformations messageTransformations;

  public MessageMappingRegistry(
    List<MessageMapping> mappings,
    ObjectMapper objectMapper
  ) {
    this.typeMappings = mappings.stream()
      .flatMap(m -> m.getTypeMappings().entrySet().stream())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    this.trustedPackages = mappings.stream()
      .flatMap(m -> m.getTrustedPackages().stream())
      .collect(Collectors.toSet());

    var allTransformations = mappings.stream()
      .flatMap(m -> m.getTransformations().stream())
      .toList();

    this.messageTransformations = new MessageTransformations(allTransformations);
  }

  public Map<String, Class<?>> getTypeMappings() {
    return typeMappings;
  }

  public Set<String> getTrustedPackages() {
    return trustedPackages;
  }

  public MessageTransformations getMessageTransformations() {
    return messageTransformations;
  }
}
