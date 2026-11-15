package io.eventdriven.eventsversioning.snapshottesting;

import io.eventdriven.strictland.MessageSerializer;
import tools.jackson.databind.ObjectMapper;

record JacksonMessageSerializer(ObjectMapper mapper) implements MessageSerializer {
  @Override
  public String fileExtension() {
    return "json";
  }

  @Override
  public byte[] serialize(Object value) {
    return mapper.writeValueAsBytes(value);
  }

  @Override
  public <T> T deserialize(byte[] bytes, Class<T> type) {
    return mapper.readValue(bytes, type);
  }
}
