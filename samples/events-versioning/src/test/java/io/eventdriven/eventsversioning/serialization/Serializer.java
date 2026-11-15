package io.eventdriven.eventsversioning.serialization;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import java.util.Optional;

public class Serializer {
  private static final Logger logger = LoggerFactory.getLogger(Serializer.class);
  public static final ObjectMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .build();

  public static byte[] serialize(Object event) {
    try {
      return mapper.writeValueAsBytes(event);
    } catch (JacksonException e) {
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
    } catch (JacksonException e) {
      logger.warn(e, () -> "Error deserializing");
      return Optional.empty();
    }
  }

  public static JsonNode deserialize(byte [] bytes){
    try {
      return mapper.readTree(bytes);
    } catch (JacksonException e) {
      logger.error(e, () -> "Error deserializing");
      throw new RuntimeException(e);
    }
  }
}
