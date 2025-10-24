package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

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
  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  public <T> Collection<T> collection(Class<T> typeClass) {
    return new Collection<>(typeClass);
  }

  public static class Collection<T> {
    private final Map<String, Object> storage = new LinkedHashMap<>();
    private final Class<T> typeClass  ;

    public Collection(Class<T> typeClass) {
      this.typeClass = typeClass;
    }

    public <T> void store(UUID id, Object obj) {
      storage.compute(getId(id), (ignore, value) -> obj);
    }

    public void delete(UUID id) {
      storage.remove(getId(id));
    }

    public Optional<T> get(UUID id) {
      return Optional.ofNullable(
        typeClass.cast(
          storage.compute(getId(id), (k, v) ->
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

    public void getAndUpdate(UUID id, Function<T, T> update) {
      try {
        var item = get(id).orElse(typeClass.getConstructor().newInstance());

        store(id, update.apply(item));
      } catch (InstantiationException | IllegalAccessException |
               InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }

    }

    private String getId(UUID id) {
      return "%s-%s".formatted(typeClass.getTypeName(), id);
    }
  }
}
