package io.eventdriven.distributedprocesses.core.serialization;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventDataBuilder;
import com.eventstore.dbclient.ResolvedEvent;
import io.eventdriven.distributedprocesses.core.commands.CommandEnvelope;
import io.eventdriven.distributedprocesses.core.commands.CommandMetadata;
import io.eventdriven.distributedprocesses.core.events.EventEnvelope;
import io.eventdriven.distributedprocesses.core.events.EventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public final class EventSerializer {
  private static final Logger logger = LoggerFactory.getLogger(EventSerializer.class);
  public static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .build();

  public static EventData serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        EventTypeMapper.toName(event.getClass()),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public static <Command> EventData serialize(CommandEnvelope<Command> commandEnvelope) {
    try {
      return EventDataBuilder.json(
          EventTypeMapper.toName(commandEnvelope.getClass()),
          mapper.writeValueAsBytes(commandEnvelope.data())
        )
        .metadataAsBytes(mapper.writeValueAsBytes(commandEnvelope.metadata()))
        .build();
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public static <Event> Optional<Event> deserialize(ResolvedEvent resolvedEvent) {
    var eventClass = EventTypeMapper.toClass(resolvedEvent.getEvent().getEventType());

    if (eventClass.isEmpty())
      return Optional.empty();

    return deserialize(eventClass.get(), resolvedEvent);
  }

  public static <Event> Optional<Event> deserialize(Class<Event> eventClass, ResolvedEvent resolvedEvent) {
    try {

      var result = mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);

      if (result == null)
        return Optional.empty();

      return Optional.of(result);
    } catch (JacksonException e) {
      logger.warn("Error deserializing event %s".formatted(resolvedEvent.getEvent().getEventType()), e);
      return Optional.empty();
    }
  }

  public static <Event> Optional<EventEnvelope<Event>> deserializeEvent(ResolvedEvent resolvedEvent) {
    var eventClass = EventTypeMapper.toClass(resolvedEvent.getEvent().getEventType());

    if (eventClass.isEmpty())
      return Optional.empty();

    return deserializeEvent(eventClass.get(), resolvedEvent);
  }

  public static <Event> Optional<EventEnvelope<Event>> deserializeEvent(Class<Event> commandClass, ResolvedEvent resolvedEvent) {
    try {
      var event = mapper.readValue(resolvedEvent.getEvent().getEventData(), commandClass);
      var metadata = mapper.readValue(resolvedEvent.getEvent().getUserMetadata(), EventMetadata.class);


      if (event == null)
        return Optional.empty();

      return Optional.of(new EventEnvelope<>(event, metadata));
    } catch (JacksonException e) {
      logger.warn("Error deserializing event %s".formatted(resolvedEvent.getEvent().getEventType()), e);
      return Optional.empty();
    }
  }

  public static <Command> Optional<CommandEnvelope<Command>> deserializeCommand(ResolvedEvent resolvedEvent) {
    var eventClass = EventTypeMapper.toClass(resolvedEvent.getEvent().getEventType());

    if (eventClass.isEmpty())
      return Optional.empty();

    return deserializeCommand(eventClass.get(), resolvedEvent);
  }

  public static <Command> Optional<CommandEnvelope<Command>> deserializeCommand(Class<Command> commandClass, ResolvedEvent resolvedEvent) {
    try {
      var event = mapper.readValue(resolvedEvent.getEvent().getEventData(), commandClass);
      var metadata = mapper.readValue(resolvedEvent.getEvent().getUserMetadata(), CommandMetadata.class);


      if (event == null)
        return Optional.empty();

      return Optional.of(new CommandEnvelope<>(event, metadata));
    } catch (JacksonException e) {
      logger.warn("Error deserializing event %s".formatted(resolvedEvent.getEvent().getEventType()), e);
      return Optional.empty();
    }
  }
}
