package io.eventdriven.distributedprocesses.core.projections;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class Database {
  private final Map<String, Object> storage = new HashMap<>();
  private static final Logger logger = LoggerFactory.getLogger(Database.class);

  public <T extends Versioned> void store(Class<T> typeClass, EntityId id, long version, T item) {
    item.setVersion(version);
    storage.put(key(typeClass, id), item);
  }

  public <T> void delete(Class<T> typeClass, EntityId id) {
    storage.remove(key(typeClass, id));
  }

  public <T> Optional<T> get(Class<T> typeClass, EntityId id) {
    return Optional.ofNullable(storage.get(key(typeClass, id))).map(item -> copy(typeClass, item));
  }

  public <T extends Versioned> void getAndUpdate(
    Class<T> typeClass,
    EntityId id,
    long version,
    Function<T, T> update
  ) {
    var item = get(typeClass, id);

    if (item.isEmpty()) {
      logger.warn("View {} with id '{}' was not found", typeClass.getSimpleName(), id.value());
      return;
    }

    // The same event replayed carries the position it had the first time,
    // so a view that is already at or past it has nothing to do.
    if (item.get().getVersion() >= version)
      return;

    store(typeClass, id, version, update.apply(item.get()));
  }

  public <T> List<T> find(Class<T> typeClass, Predicate<T> filter) {
    var prefix = typeClass.getTypeName() + "-";
    var results = new ArrayList<T>();

    for (var entry : storage.entrySet()) {
      if (!entry.getKey().startsWith(prefix))
        continue;

      var item = copy(typeClass, entry.getValue());

      if (filter.test(item))
        results.add(item);
    }

    return List.copyOf(results);
  }

  // Round-tripping through JSON hands out a copy, so a caller mutating
  // what it read cannot change what the next reader sees.
  private static <T> T copy(Class<T> typeClass, Object item) {
    try {
      return mapper.readValue(mapper.writeValueAsString(item), typeClass);
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> String key(Class<T> typeClass, EntityId id) {
    return "%s-%s".formatted(typeClass.getTypeName(), id.value());
  }

  private static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();
}
