package io.eventdriven.introductiontoeventsourcing.e11_projections_singlestream_eventual_consistency.tools;

import tools.jackson.databind.cfg.DateTimeFeature;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.InvocationTargetException;
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
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();

  public <T> void store(Class<T> typeClass, UUID id, Object obj) {
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
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> void getAndUpdate(Class<T> typeClass, UUID id, Function<T, T> update) {
    try {
      var item = get(typeClass, id).orElse(typeClass.getConstructor().newInstance());

      store(typeClass, id, update.apply(item));
    } catch (InstantiationException | IllegalAccessException |
             InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }

  }

  private static <T> String getId(Class<T> typeClass, UUID id) {
    return "%s-%s".formatted(typeClass.getTypeName(), id);
  }
}
