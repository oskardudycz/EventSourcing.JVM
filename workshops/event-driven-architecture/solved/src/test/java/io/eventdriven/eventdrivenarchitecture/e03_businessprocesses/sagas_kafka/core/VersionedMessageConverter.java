package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning.MessageTransformations;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import tools.jackson.databind.ObjectMapper;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getMessageType;

public class VersionedMessageConverter {
  private final JacksonJsonDeserializer<Object> jsonDeserializer;
  private final ObjectMapper objectMapper;
  private final MessageTransformations transformations;

  public VersionedMessageConverter(
    JacksonJsonDeserializer<Object> jsonDeserializer,
    ObjectMapper objectMapper,
    MessageTransformations transformations
  ) {
    this.jsonDeserializer = jsonDeserializer;
    this.objectMapper = objectMapper;
    this.transformations = transformations;
  }

  public Object read(ConsumerRecord<?, ?> record) {
    var messageType = getMessageType(record);

    try {
      var data = (byte[]) record.value();

      var transformed = transformations.tryTransform(objectMapper, messageType, data);
      if (transformed.isPresent())
        return transformed.get();

      return jsonDeserializer.deserialize(record.topic(), record.headers(), data);
    } catch (Exception e) {
      throw new UnreadableMessageException(messageType, e);
    }
  }
}
