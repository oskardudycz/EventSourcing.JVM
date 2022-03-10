package io.eventdriven.ecommerce.core.serialization;

import com.eventstore.dbclient.EventData;
import com.eventstore.dbclient.ResolvedEvent;
import io.eventdriven.ecommerce.core.events.EventTypeMapper;

import java.io.IOException;
import java.util.UUID;

public final class EventSerializer {
  public static EventData Serialize(Object event){
    return EventData.builderAsJson(
      UUID.randomUUID(),
      EventTypeMapper.ToName(event.getClass()),
      event
    ).build();
  }

  public static <TEvent> TEvent Deserialize(ResolvedEvent resolvedEvent){
    var eventClass = EventTypeMapper.ToClass(resolvedEvent.getEvent().getEventType());
    try {
      var result = resolvedEvent.getEvent().getEventDataAs(eventClass);

      if(result == null)
        return null;

      return (TEvent) result;
    } catch (IOException ex) {
      return null;
    }
  }
}
