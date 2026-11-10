package io.eventdriven.eventdrivenarchitecture.e05_consistency.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class Database {
  private static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();

  private final Map<Class<?>, LinkedHashMap<String, Object>> storage;
  private final TransientFailures failures;

  public Database() {
    this(new LinkedHashMap<>(), new TransientFailures("Database not available!"));
  }

  private Database(Map<Class<?>, LinkedHashMap<String, Object>> storage, TransientFailures failures) {
    this.storage = storage;
    this.failures = failures;
  }

  public <T> Collection<T> collection(Class<T> typeClass) {
    return new Collection<>(typeClass, this);
  }

  // Changes made in the operations are committed together, or not at all if the operations or the commit fail
  public void transaction(Consumer<Database> operations) {
    var transactionalStorage = new LinkedHashMap<Class<?>, LinkedHashMap<String, Object>>();
    storage.forEach((type, documents) -> transactionalStorage.put(type, new LinkedHashMap<>(documents)));

    operations.accept(new Database(transactionalStorage, null));

    failIfUnavailable();

    transactionalStorage.forEach((type, documents) -> {
      var committed = storage.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
      committed.clear();
      committed.putAll(documents);
    });
  }

  private void failIfUnavailable() {
    if (failures != null)
      failures.failIfUnavailable();
  }

  public static class Collection<T> {
    private final Map<String, Object> storage;
    private final Class<T> typeClass;
    private final Database database;

    public Collection(Class<T> typeClass, Database database) {
      this.typeClass = typeClass;
      this.database = database;
      this.storage = database.storage.computeIfAbsent(typeClass, ignored -> new LinkedHashMap<>());
    }

    public void store(UUID id, Object obj) {
      database.failIfUnavailable();

      storage.put(getId(id), obj);
    }

    public void delete(UUID id) {
      database.failIfUnavailable();

      storage.remove(getId(id));
    }

    public Optional<T> get(UUID id) {
      var value = storage.get(getId(id));

      return value != null ? Optional.of(clone(value)) : Optional.empty();
    }

    public List<T> findAll() {
      return storage.values().stream().map(this::clone).toList();
    }

    // Clone to simulate getting new instance on loading
    private T clone(Object value) {
      try {
        return mapper.readValue(mapper.writeValueAsString(value), typeClass);
      } catch (JacksonException e) {
        throw new RuntimeException(e);
      }
    }

    private String getId(UUID id) {
      return "%s-%s".formatted(typeClass.getTypeName(), id);
    }
  }
}
