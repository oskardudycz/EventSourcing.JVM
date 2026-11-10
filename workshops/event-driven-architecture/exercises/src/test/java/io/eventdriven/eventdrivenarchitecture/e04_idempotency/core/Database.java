package io.eventdriven.eventdrivenarchitecture.e04_idempotency.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Database {
  private static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();


  private Map<Class, LinkedHashMap<String, Object>> storage = new LinkedHashMap<>();

  public <T> Collection<T> collection(Class<T> typeClass) {
    return new Collection<>(typeClass, this);
  }

  public void transaction(Consumer<Database> operations) {
    var transactionalStorage = this.storage.entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        v -> new LinkedHashMap<>(v.getValue())
      ));
    var transactionalDb = new Database();
    transactionalDb.storage = transactionalStorage;

    try {
      operations.accept(transactionalDb);
      this.storage = transactionalStorage;
    } catch (Exception e) {
      // Do nothing, discard transactional changes
      System.out.println(e.getMessage());
      throw e;
    }
  }

  public static class Collection<T> {
    private final Map<String, Object> storage;
    private final Class<T> typeClass;

    public Collection(Class<T> typeClass, Database database) {
      this.typeClass = typeClass;
      this.storage = database.storage.computeIfAbsent(
        typeClass,
        ignored -> new LinkedHashMap<>()
      );
    }

    public void store(UUID id, Object obj) {
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
            } catch (JacksonException e) {
              throw new RuntimeException(e);
            }
          })));
    }

    public List<T> findAll() {
      return storage.values().stream()
        .map(v -> {
          try {
            return mapper.readValue(mapper.writeValueAsString(v), typeClass);
          } catch (JacksonException e) {
            throw new RuntimeException(e);
          }
        })
        .toList();
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
