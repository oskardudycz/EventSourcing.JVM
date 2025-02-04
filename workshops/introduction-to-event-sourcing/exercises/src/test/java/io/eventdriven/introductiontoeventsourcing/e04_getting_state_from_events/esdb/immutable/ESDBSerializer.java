package io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.esdb.immutable;

import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.EventDataBuilder;
import com.eventstore.dbclient.ResolvedEvent;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.UUID;

public class ESDBSerializer {
  public static final ObjectMapper mapper =
    new JsonMapper()
    .registerModule(new JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
    .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);


  private EventData serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        event.getClass().getTypeName(),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public Object deserialize(ResolvedEvent resolvedEvent) {
    try {
      var eventClass = Class.forName(
        resolvedEvent.getOriginalEvent().getEventType());
      return mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
