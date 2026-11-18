package io.eventdriven.buildyourowneventstore;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class JsonEventSerializer {
    private static final Logger logger = LoggerFactory.getLogger(JsonEventSerializer.class);
    public static final ObjectMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build();

    public static String serialize(Object event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    public static <Event> Optional<Event> deserialize(Class<Event> eventClass, String eventType, String payload) {
        try {

            var result = mapper.readValue(payload, eventClass);

            if (result == null) return Optional.empty();

            return Optional.of(result);
        } catch (JacksonException e) {
            logger.warn("Error deserializing event %s".formatted(eventType, e));
            return Optional.empty();
        }
    }
}
