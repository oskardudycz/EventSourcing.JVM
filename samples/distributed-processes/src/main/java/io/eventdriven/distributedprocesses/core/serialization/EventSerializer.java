package io.eventdriven.distributedprocesses.core.serialization;

import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventDataBuilder;
import com.eventstore.dbclient.ResolvedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.distributedprocesses.core.commands.CommandEnvelope;
import io.eventdriven.distributedprocesses.core.commands.CommandMetadata;
import io.eventdriven.distributedprocesses.core.events.EventEnvelope;
import io.eventdriven.distributedprocesses.core.events.EventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class EventSerializer {
  private static final Logger logger = LoggerFactory.getLogger(EventSerializer.class);
  public static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

  public static EventData serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        EventTypeMapper.toName(event.getClass()),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static <Command> EventData serialize(CommandEnvelope<Command> commandEnvelope) {
    try {
      return new EventData(
        UUID.randomUUID(),
        EventTypeMapper.toName(commandEnvelope.getClass()),
        "application/json",
        mapper.writeValueAsBytes(commandEnvelope.data()),
        mapper.writeValueAsBytes(commandEnvelope.metadata())
      );
    } catch (JsonProcessingException e) {
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
    } catch (IOException e) {
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


      if(event == null)
        return Optional.empty();

      return Optional.of(new EventEnvelope<>(event, metadata));
    } catch (IOException e) {
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


      if(event == null)
        return Optional.empty();

      return Optional.of(new CommandEnvelope<>(event, metadata));
    } catch (IOException e) {
      logger.warn("Error deserializing event %s".formatted(resolvedEvent.getEvent().getEventType()), e);
      return Optional.empty();
    }
  }
}
