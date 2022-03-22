package io.eventdriven.ecommerce.core.events;

import com.eventstore.dbclient.ResolvedEvent;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;

public record EventEnvelope<Event>(
  Event data,
  EventMetadata metadata
) {
  public static <Event> EventEnvelope<Event> From(final Class<Event> type, ResolvedEvent resolvedEvent){
    if(type == null)
      return null;

    return new EventEnvelope<>(
      EventSerializer.Deserialize(type, resolvedEvent),
      new EventMetadata(
        resolvedEvent.getEvent().getEventId().toString(),
        resolvedEvent.getEvent().getStreamRevision().getValueUnsigned(),
        resolvedEvent.getEvent().getPosition().getCommitUnsigned(),
        resolvedEvent.getEvent().getEventType()
      )
    );
  }
}
