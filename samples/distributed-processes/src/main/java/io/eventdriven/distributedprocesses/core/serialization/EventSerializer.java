package io.eventdriven.distributedprocesses.core.serialization;

import io.eventdriven.distributedprocesses.core.commands.CommandEnvelope;
import io.eventdriven.distributedprocesses.core.commands.CommandMetadata;
import io.eventdriven.distributedprocesses.core.events.EventEnvelope;
import io.eventdriven.distributedprocesses.core.events.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageValues;
import io.kurrent.dbclient.EventData;
import io.kurrent.dbclient.EventDataBuilder;
import io.kurrent.dbclient.ResolvedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class EventSerializer {
    private static final Logger logger = LoggerFactory.getLogger(EventSerializer.class);
    public static final String CORRELATION_ID = "$correlationId";
    public static final String CAUSATION_ID = "$causationId";
    public static final String TRACE_ID = "$traceId";
    public static final String SPAN_ID = "$spanId";
    public static final JsonMapper mapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build();

    public static EventData serialize(Object event) {
        try {
            return json(UUID.randomUUID(), event).build();
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    private static EventDataBuilder json(UUID eventId, Object event) {
        return EventDataBuilder.json(
                eventId, EventTypeMapper.toName(event.getClass()), mapper.writeValueAsBytes(event));
    }

    public static EventData serialize(Object event, MessageValues values) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put(CORRELATION_ID, values.correlationId());
        metadata.put(CAUSATION_ID, values.causationId());
        if (values.traceId() != null) metadata.put(TRACE_ID, values.traceId());
        if (values.spanId() != null) metadata.put(SPAN_ID, values.spanId());

        try {
            return json(UUID.fromString(values.messageId()), event)
                    .metadataAsBytes(mapper.writeValueAsBytes(metadata))
                    .build();
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, String> userMetadataOf(ResolvedEvent resolvedEvent) {
        var raw = resolvedEvent.getEvent().getUserMetadata();

        if (raw == null || raw.length == 0) return Map.of();

        var values = new LinkedHashMap<String, String>();
        try {
            var parsed = mapper.readValue(raw, new TypeReference<Map<String, Object>>() {});

            if (parsed == null) return Map.of();

            parsed.forEach((key, value) -> {
                if (value instanceof String || value instanceof Number || value instanceof Boolean)
                    values.put(key, value.toString());
            });
        } catch (JacksonException e) {
            return Map.of();
        }
        return values;
    }

    public static <Command> EventData serialize(CommandEnvelope<Command> commandEnvelope) {
        try {
            return EventDataBuilder.json(
                            EventTypeMapper.toName(commandEnvelope.getClass()),
                            mapper.writeValueAsBytes(commandEnvelope.data()))
                    .metadataAsBytes(mapper.writeValueAsBytes(commandEnvelope.metadata()))
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

    public static <Event> Optional<EventEnvelope<Event>> deserializeEvent(ResolvedEvent resolvedEvent) {
        var eventClass = EventTypeMapper.toClass(resolvedEvent.getEvent().getEventType());

        if (eventClass.isEmpty()) return Optional.empty();

        return deserializeEvent(eventClass.get(), resolvedEvent);
    }

    public static <Event> Optional<EventEnvelope<Event>> deserializeEvent(
            Class<Event> commandClass, ResolvedEvent resolvedEvent) {
        try {
            var event = mapper.readValue(resolvedEvent.getEvent().getEventData(), commandClass);
            var metadata = mapper.readValue(resolvedEvent.getEvent().getUserMetadata(), EventMetadata.class);

            if (event == null) return Optional.empty();

            return Optional.of(new EventEnvelope<>(event, metadata));
        } catch (JacksonException e) {
            logger.warn(
                    "Error deserializing event %s"
                            .formatted(resolvedEvent.getEvent().getEventType()),
                    e);
            return Optional.empty();
        }
    }

    public static <Command> Optional<CommandEnvelope<Command>> deserializeCommand(ResolvedEvent resolvedEvent) {
        var eventClass = EventTypeMapper.toClass(resolvedEvent.getEvent().getEventType());

        if (eventClass.isEmpty()) return Optional.empty();

        return deserializeCommand(eventClass.get(), resolvedEvent);
    }

    public static <Command> Optional<CommandEnvelope<Command>> deserializeCommand(
            Class<Command> commandClass, ResolvedEvent resolvedEvent) {
        try {
            var event = mapper.readValue(resolvedEvent.getEvent().getEventData(), commandClass);
            var metadata = mapper.readValue(resolvedEvent.getEvent().getUserMetadata(), CommandMetadata.class);

            if (event == null) return Optional.empty();

            return Optional.of(new CommandEnvelope<>(event, metadata));
        } catch (JacksonException e) {
            logger.warn(
                    "Error deserializing event %s"
                            .formatted(resolvedEvent.getEvent().getEventType()),
                    e);
            return Optional.empty();
        }
    }
}
