package io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.tools;

import tools.jackson.databind.cfg.DateTimeFeature;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.Projections;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Database {
  private final Map<String, Object> storage = new HashMap<>();

  private static final ObjectMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();

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
          } catch (JacksonException e) {
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
