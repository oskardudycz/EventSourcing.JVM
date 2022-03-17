package io.eventdriven.ecommerce.core.events;

import com.eventstore.dbclient.ResolvedEvent;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;

public record EventEnvelope<TEvent>(
  TEvent data,
  EventMetadata metadata
) {
  public static <TEvent> EventEnvelope<TEvent> From(final Class<TEvent> type, ResolvedEvent resolvedEvent){
    return new EventEnvelope<>(
      EventSerializer.Deserialize(resolvedEvent),
      new EventMetadata(
        resolvedEvent.getEvent().getEventId().toString(),
        resolvedEvent.getEvent().getStreamRevision().getValueUnsigned(),
        resolvedEvent.getEvent().getPosition().getCommitUnsigned()
      )
    );
  }
}
