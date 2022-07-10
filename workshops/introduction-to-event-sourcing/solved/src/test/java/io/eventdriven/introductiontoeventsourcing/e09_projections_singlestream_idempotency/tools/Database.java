package io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream_idempotency.tools;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream_idempotency.Projections;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Database {
  private final Map<String, Object> storage = new HashMap<>();

  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  public <T extends Projections.Versioned> void store(Class<T> typeClass, UUID id, long expectedVersion, T obj) {
    obj.setVersion(expectedVersion);
    storage.compute(getId(typeClass, id), (ignore, value) -> obj);
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

  public <T extends Projections.Versioned> void getAndUpdate(Class<T> typeClass, UUID id, long version, Function<T, T> update) {
    var item = get(typeClass, id)
      .orElseThrow(()-> new RuntimeException("Item with id: '%s' and expected version: %s not found!".formatted(id, version)));

    if (item.getVersion() >= version) return;

    store(typeClass, id, version, update.apply(item));
  }

  private static <T> String getId(Class<T> typeClass, UUID id) {
    return "%s-%s".formatted(typeClass.getTypeName(), id);
  }
}
