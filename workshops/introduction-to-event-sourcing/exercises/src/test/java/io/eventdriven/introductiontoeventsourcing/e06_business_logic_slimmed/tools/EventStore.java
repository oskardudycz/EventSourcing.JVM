package io.eventdriven.introductiontoeventsourcing.e06_business_logic_slimmed.tools;


import tools.jackson.databind.cfg.DateTimeFeature;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
            } catch (JacksonException ex) {
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
      } catch (JacksonException | ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }).toList();
  }


  record EventEnvelope(String eventType, String json) {
  }

  private static final ObjectMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();

}
