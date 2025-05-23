package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Database {
  private final Map<String, Object> storage = new LinkedHashMap<>();

  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  public <T> void store(UUID id, Object obj) {
    storage.compute(getId(obj.getClass(), id), (ignore, value) -> obj);
  }

  public <T> void delete(Class<T> typeClass, UUID id) {
    storage.remove(getId(typeClass, id));
  }

  public <T> Optional<T> get(Class<T> typeClass, UUID id) {
    return Optional.ofNullable(
      typeClass.cast(
        storage.compute(getId(typeClass, id), (k, v) ->
        {
          try {
            return v != null ?
              mapper.readValue(mapper.writeValueAsString(v), typeClass)
              : null;
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        })));
  }

  public <T> void getAndUpdate(Class<T> typeClass, UUID id, Function<T, T> update) {
    try {
      var item = get(typeClass, id).orElse(typeClass.getConstructor().newInstance());

      store(id, update.apply(item));
    } catch (InstantiationException | IllegalAccessException |
             InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }

  }

  private static <T> String getId(Class<T> typeClass, UUID id) {
    return "%s-%s".formatted(typeClass.getTypeName(), id);
  }
}
