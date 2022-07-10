package io.eventdriven.introductiontoeventsourcing.solved.e10_projections_singlestream_eventual_consistency.tools;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.introductiontoeventsourcing.solved.e10_projections_singlestream_eventual_consistency.Projections;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;

public class Database {
  public record DataWrapper(Object data, OffsetDateTime validFrom) {
  }

  private final Map<String, List<DataWrapper>> storage = new HashMap<>();
  private final Random random = new Random();

  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  public <T extends Projections.Versioned> void store(Class<T> typeClass, UUID id, T obj) {
    storage.compute(getId(typeClass, id), (ignore, values) -> {
        if (values == null)
          values = new ArrayList<>();

      var lastValidFrom = values.stream()
        .filter(i -> i.validFrom().isBefore(OffsetDateTime.now()))
        .reduce((first, second) -> first.validFrom().isBefore(second.validFrom()) ? second : first)
        .map(DataWrapper::validFrom)
        .orElse(OffsetDateTime.now());

        var validFrom = lastValidFrom.plus(Duration.ofMillis(random.nextInt(50, 100)));

        values.add(new DataWrapper(obj, validFrom));

        return values;
      }
    );
  }

  public <T> void delete(Class<T> typeClass, UUID id) {
    storage.remove(getId(typeClass, id));
  }

  public <T> Optional<T> get(Class<T> typeClass, UUID id) {
    var values = storage.get(getId(typeClass, id));

    if (values == null)
      return Optional.empty();

    var item = values.stream()
      .filter(i -> i.validFrom().isBefore(OffsetDateTime.now()))
      .reduce((first, second) -> second)
      .orElse(null);

    if (item == null)
      return Optional.empty();

    try {
      return Optional.ofNullable(
        typeClass.cast(mapper.readValue(mapper.writeValueAsString(item.data()), typeClass))
      );
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public <T extends Projections.Versioned> void getAndUpdate(Class<T> typeClass, UUID id, long currentVersion, Function<T, T> update) {
    var expectedVersion = currentVersion - 1;

    var item = getExpectingGreaterOrEqualVersionWithRetries(typeClass, id, expectedVersion).orElseThrow(
      () -> new RuntimeException("Item with id: %s and expected version: %s not found!".formatted(id, currentVersion))
    );
    if (item.getVersion() > expectedVersion)
      return;

    item.setVersion(currentVersion);

    store(typeClass, id, update.apply(item));
  }

  public <T extends Projections.Versioned> Optional<T> getExpectingGreaterOrEqualVersionWithRetries(Class<T> typeClass, UUID id, long expectedVersion) {
    Optional<T> item;
    var triesLeft = 4;

    do {
      item = get(typeClass, id);

      if (item.isPresent() && item.get().getVersion() < expectedVersion)
        item = Optional.empty();

      triesLeft--;

      if (triesLeft > 0) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

    } while (item.isEmpty() && triesLeft > 0);

    return item;
  }

  private static <T> String getId(Class<T> typeClass, UUID id) {
    return "%s-%s".formatted(typeClass.getTypeName(), id);
  }
}
