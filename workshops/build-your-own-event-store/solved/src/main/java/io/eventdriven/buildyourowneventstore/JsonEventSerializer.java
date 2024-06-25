package io.eventdriven.buildyourowneventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class JsonEventSerializer {
    private static final Logger logger = LoggerFactory.getLogger(JsonEventSerializer.class);
    public static final ObjectMapper mapper =
        new JsonMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public static String serialize(Object event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <Event> Optional<Event> deserialize(Class<Event> eventClass, String eventType, String payload) {
        try {

            var result = mapper.readValue(payload, eventClass);

            if (result == null)
                return Optional.empty();

            return Optional.of(result);
        } catch (IOException e) {
            logger.warn("Error deserializing event %s".formatted(eventType, e));
            return Optional.empty();
        }
    }
}
