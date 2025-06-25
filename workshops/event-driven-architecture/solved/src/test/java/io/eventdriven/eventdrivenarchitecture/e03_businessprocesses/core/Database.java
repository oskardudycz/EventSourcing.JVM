package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;
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
import java.util.function.Consumer;
import java.util.function.Function;

public class Database {
  private Map<String, String> storage = new LinkedHashMap<>();

  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  public <T> void store(UUID id, Object obj) {
    storage.compute(getId(obj.getClass(), id), (ignore, value) -> {
      try {
        return mapper.writeValueAsString(obj);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public <T> void delete(Class<T> typeClass, UUID id) {
    storage.remove(getId(typeClass, id));
  }

  public <T> Optional<T> get(Class<T> typeClass, UUID id) {
    var key = getId(typeClass, id);
    if(!storage.containsKey(key)) {
      return Optional.empty();
    }
    try {
      return Optional.of(mapper.readValue(storage.get(key), typeClass));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
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

  public void transaction(Consumer<Database> operations) {
    var transactionalStorage = new LinkedHashMap<>(this.storage);
    var transactionalDb = new Database();
    transactionalDb.storage = transactionalStorage;

    try {
      operations.accept(transactionalDb);
      this.storage = transactionalStorage;
    } catch (Exception e) {
      // Do nothing, discard transactional changes
      throw e;
    }
  }

  private static <T> String getId(Class<T> typeClass, UUID id) {
    return "%s-%s".formatted(typeClass.getTypeName(), id);
  }
}
