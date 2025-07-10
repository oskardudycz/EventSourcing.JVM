package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Optional;

public final class MessageSerializer {
    private static final ObjectMapper mapper = new JsonMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    public static String serialize(Object message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message: " + message.getClass().getSimpleName(), e);
        }
    }

    public static <T> Optional<T> deserialize(Class<T> messageClass, String json) {
        try {
            T result = mapper.readValue(json, messageClass);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            System.err.println("Failed to deserialize message of type " + messageClass.getSimpleName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<Object> deserialize(String eventTypeName, String json) {
        var eventClass = EventTypeMapper.toClass(eventTypeName);
        if (eventClass.isEmpty()) {
            System.err.println("Could not find class for event type: " + eventTypeName);
            return Optional.empty();
        }

        try {
            Object result = mapper.readValue(json, eventClass.get());
            return Optional.ofNullable(result);
        } catch (Exception e) {
            System.err.println("Failed to deserialize message of type " + eventTypeName + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}