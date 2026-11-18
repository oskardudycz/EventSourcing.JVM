package io.eventdriven.ecommerce.core.serialization;

import io.eventdriven.ecommerce.core.events.EventTypeMapper;
import io.kurrent.dbclient.EventData;
import io.kurrent.dbclient.EventDataBuilder;
import io.kurrent.dbclient.ResolvedEvent;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class EventSerializer {
    private static final Logger logger = LoggerFactory.getLogger(EventSerializer.class);
    public static final JsonMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build();

    public static EventData serialize(Object event) {
        try {
            return EventDataBuilder.json(
                            UUID.randomUUID(),
                            EventTypeMapper.toName(event.getClass()),
                            mapper.writeValueAsBytes(event))
                    .build();
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    public static <Event> Optional<Event> deserialize(ResolvedEvent resolvedEvent) {
        var eventClass = EventTypeMapper.toClass(resolvedEvent.getEvent().getEventType());

        if (eventClass.isEmpty()) return Optional.empty();

        return deserialize(eventClass.get(), resolvedEvent);
    }

    public static <Event> Optional<Event> deserialize(Class<Event> eventClass, ResolvedEvent resolvedEvent) {
        try {

            var result = mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);

            if (result == null) return Optional.empty();

            return Optional.of(result);
        } catch (JacksonException e) {
            logger.warn(
                    "Error deserializing event %s"
                            .formatted(resolvedEvent.getEvent().getEventType()),
                    e);
            return Optional.empty();
        }
    }
}
