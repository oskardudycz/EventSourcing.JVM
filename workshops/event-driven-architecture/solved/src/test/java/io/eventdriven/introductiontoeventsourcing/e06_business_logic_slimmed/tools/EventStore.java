package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.tools;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.*;

public class EventStore {
  private final Map<UUID, List<EventEnvelope>> events = new HashMap<>();

  public void appendToStream(UUID streamId, Object[] newEvents) {
    events.compute(streamId, (stream, events) -> {
      if (events == null)
        events = new ArrayList<>();

      events.addAll(
        Arrays.stream(newEvents)
          .map(e -> {
            try {
              return new EventEnvelope(e.getClass().getTypeName(), mapper.writeValueAsString(e));
            } catch (JsonProcessingException ex) {
              throw new RuntimeException(ex);
            }
          }).toList()
      );

      return events;
    });
  }


  public <Event> List<Event> readStream(Class<Event> typeClass, UUID streamId) {
    return events.getOrDefault(streamId, new ArrayList<>()).stream().map(eventEnvelope -> {
      try {
        var eventClass = Class.forName(eventEnvelope.eventType);
        return typeClass.cast(mapper.readValue(eventEnvelope.json, eventClass));
      } catch (IOException | ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }).toList();
  }


  record EventEnvelope(String eventType, String json) {
  }

  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

}
