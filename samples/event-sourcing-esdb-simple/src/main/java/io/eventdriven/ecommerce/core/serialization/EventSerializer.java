package io.eventdriven.ecommerce.core.serialization;

import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventDataBuilder;
import com.eventstore.dbclient.ResolvedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.ecommerce.core.events.EventTypeMapper;

import java.io.IOException;
import java.util.UUID;

public final class EventSerializer {
  public static final JsonMapper mapper = (JsonMapper) new JsonMapper()
    .registerModule(new JavaTimeModule())
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

  public static EventData Serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        EventTypeMapper.ToName(event.getClass()),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static <TEvent> TEvent Deserialize(ResolvedEvent resolvedEvent) {
    var result = Deserialize(
      EventTypeMapper.ToClass(resolvedEvent.getEvent().getEventType()),
      resolvedEvent
    );
    if (result == null)
      return null;

    return (TEvent) result;
  }

  public static <TEvent> TEvent Deserialize(Class<TEvent> eventClass, ResolvedEvent resolvedEvent) {
    try {
      var result = mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);

      if (result == null)
        return null;

      return (TEvent) result;
    } catch (IOException ex) {
      return null;
    }
  }
}
