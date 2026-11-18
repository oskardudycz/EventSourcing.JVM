package io.eventdriven.introductiontoeventsourcing.e10_projections_singlestream_idempotency.tools;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class Database {
    private final Map<String, Object> storage = new HashMap<>();

    private static final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
            .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .build();

    public <T> void store(Class<T> typeClass, UUID id, Object obj) {
        storage.compute(getId(typeClass, id), (ignore, value) -> obj);
    }

    public <T> void delete(Class<T> typeClass, UUID id) {
        storage.remove(getId(typeClass, id));
    }

    public <T> Optional<T> get(Class<T> typeClass, UUID id) {
        return Optional.ofNullable(typeClass.cast(storage.compute(getId(typeClass, id), (k, v) -> {
            try {
                return v != null ? mapper.readValue(mapper.writeValueAsString(v), typeClass) : null;
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        })));
    }

    public <T> void getAndUpdate(Class<T> typeClass, UUID id, Function<T, T> update) {
        try {
            var item = get(typeClass, id).orElse(typeClass.getConstructor().newInstance());

            store(typeClass, id, update.apply(item));
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> String getId(Class<T> typeClass, UUID id) {
        return "%s-%s".formatted(typeClass.getTypeName(), id);
    }
}
