package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Database {
  private static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();


  private Map<Class, LinkedHashMap<String, Versioned<Object>>> storage = new LinkedHashMap<>();

  public <T> Collection<T> collection(Class<T> typeClass) {
    return new Collection<>(typeClass, this);
  }

  public static class Collection<T> {
    private final Map<String, Versioned<Object>> storage;
    private final Class<T> typeClass;

    public Collection(Class<T> typeClass, Database database) {
      this.typeClass = typeClass;
      synchronized (database) {
        this.storage = database.storage.computeIfAbsent(
          typeClass,
          ignored -> new LinkedHashMap<>()
        );
      }
    }

    public void store(UUID id, Object obj) {
      synchronized (storage) {
        storage.put(getId(id), new Versioned<>(obj, currentVersion(id) + 1));
      }
    }

    public void store(UUID id, Object obj, int expectedVersion) {
      synchronized (storage) {
        var currentVersion = currentVersion(id);

        if (expectedVersion != currentVersion)
          throw new ConcurrencyException(
            "Expected version %d of %s '%s', but it's %d".formatted(expectedVersion, typeClass.getSimpleName(), id, currentVersion)
          );

        storage.put(getId(id), new Versioned<>(obj, currentVersion + 1));
      }
    }

    public void delete(UUID id) {
      synchronized (storage) {
        storage.remove(getId(id));
      }
    }

    public Optional<Versioned<T>> get(UUID id) {
      Optional<Versioned<T>> result;

      synchronized (storage) {
        result = Optional.ofNullable(storage.get(getId(id)))
          // Clone to simulate getting new instance on loading
          .map(stored -> new Versioned<>(clone(stored.document()), stored.version()));
      }

      respondWithDelay();

      return result;
    }

    private int currentVersion(UUID id) {
      var stored = storage.get(getId(id));
      return stored != null ? stored.version() : 0;
    }

    private T clone(Object document) {
      try {
        return mapper.readValue(mapper.writeValueAsString(document), typeClass);
      } catch (JacksonException e) {
        throw new RuntimeException(e);
      }
    }

    // Respond with a delay, like a real database, so handlers running in parallel read before others store
    private static void respondWithDelay() {
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    private String getId(UUID id) {
      return "%s-%s".formatted(typeClass.getTypeName(), id);
    }
  }
}
