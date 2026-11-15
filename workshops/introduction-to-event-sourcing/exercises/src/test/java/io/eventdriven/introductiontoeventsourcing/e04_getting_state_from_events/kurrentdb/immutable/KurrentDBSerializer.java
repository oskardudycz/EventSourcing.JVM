package io.eventdriven.introductiontoeventsourcing.e04_getting_state_from_events.kurrentdb.immutable;

import tools.jackson.databind.cfg.DateTimeFeature;
import io.kurrent.dbclient.EventData;
import io.kurrent.dbclient.EventDataBuilder;
import io.kurrent.dbclient.ResolvedEvent;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

public class KurrentDBSerializer {
  public static final ObjectMapper mapper =
    JsonMapper.builder()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
    .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
    .build();


  private EventData serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        event.getClass().getTypeName(),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public Object deserialize(ResolvedEvent resolvedEvent) {
    try {
      var eventClass = Class.forName(
        resolvedEvent.getOriginalEvent().getEventType());
      return mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);
    } catch (JacksonException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
