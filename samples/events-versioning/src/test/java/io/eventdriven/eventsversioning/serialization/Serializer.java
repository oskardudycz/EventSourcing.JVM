package io.eventdriven.eventsversioning.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class Serializer {
  private static final Logger logger = LoggerFactory.getLogger(Serializer.class);
  public static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

  public static byte[] serialize(Object event) {
    try {
      return mapper.writeValueAsBytes(event);
    } catch (JsonProcessingException e) {
      logger.error(e, () -> "Error serializing");
      throw new RuntimeException(e);
    }
  }

  public static <ResultType> Optional<ResultType> deserialize(Class<ResultType> resultTypeClass, byte[] bytes) {
    try {
      var result = mapper.readValue(bytes, resultTypeClass);

      if (result == null)
        return Optional.empty();

      return Optional.of(result);
    } catch (IOException e) {
      logger.warn(e, () -> "Error deserializing");
      return Optional.empty();
    }
  }

  public static JsonNode deserialize(byte [] bytes){
    try {
      return mapper.readTree(bytes);
    } catch (IOException e) {
      logger.error(e, () -> "Error deserializing");
      throw new RuntimeException(e);
    }
  }
}
