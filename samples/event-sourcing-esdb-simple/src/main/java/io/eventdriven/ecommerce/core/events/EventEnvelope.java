package io.eventdriven.ecommerce.core.events;

import com.eventstore.dbclient.ResolvedEvent;
import io.eventdriven.ecommerce.core.serialization.EventSerializer;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

import java.util.Optional;

public record EventEnvelope<Event>(
  Event data,
  EventMetadata metadata
) implements ResolvableTypeProvider {

  public static <Event> Optional<EventEnvelope<Event>> of(final Class<Event> type, ResolvedEvent resolvedEvent) {
    if (type == null)
      return Optional.empty();

    var eventData = EventSerializer.deserialize(type, resolvedEvent);

    if (eventData.isEmpty())
      return Optional.empty();

    return Optional.of(
      new EventEnvelope<>(
        eventData.get(),
        new EventMetadata(
          resolvedEvent.getEvent().getEventId().toString(),
          resolvedEvent.getEvent().getStreamRevision().getValueUnsigned(),
          resolvedEvent.getEvent().getPosition().getCommitUnsigned(),
          resolvedEvent.getEvent().getEventType()
        )
      )
    );
  }

  @Override
  public ResolvableType getResolvableType() {
    return ResolvableType.forClassWithGenerics(
      getClass(), ResolvableType.forInstance(data)
    );
  }
}
