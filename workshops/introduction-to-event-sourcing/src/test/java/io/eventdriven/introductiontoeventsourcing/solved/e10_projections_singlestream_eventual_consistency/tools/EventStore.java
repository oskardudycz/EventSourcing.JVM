package io.eventdriven.introductiontoeventsourcing.solved.e10_projections_singlestream_eventual_consistency.tools;

import java.util.*;
import java.util.function.Consumer;

import static io.eventdriven.introductiontoeventsourcing.solved.e10_projections_singlestream_eventual_consistency.tools.EventEnvelopeBase.EventEnvelope;
import static io.eventdriven.introductiontoeventsourcing.solved.e10_projections_singlestream_eventual_consistency.tools.EventEnvelopeBase.EventMetadata;

public class EventStore {
  private final Map<String, List<Consumer<EventEnvelopeBase>>> handlers = new HashMap<>();
  private final Map<UUID, List<EventEnvelopeBase>> events = new HashMap<>();

  private final Random random = new Random();

  public <Event> void append(UUID streamId, Event event) {
    events.compute(streamId, (stream, events) -> {
      if (events == null)
        events = new ArrayList<>();

      var eventEnvelope =
        new EventEnvelope<>(event, EventMetadata.of(events.size(), getCurrentLogPosition()));

      events.add(eventEnvelope);

      var eventHandlers = handlers.get(event.getClass().getTypeName());
      if (eventHandlers == null)
        return events;

      for (var handle : eventHandlers) {
        // Adding idempotency
        var numberOfRepeatedPublish = random.nextInt(1, 5);

        do
        {
          handle.accept(eventEnvelope);
        } while (--numberOfRepeatedPublish > 0);
      }

      return events;
    });
  }

  public <Event> void subscribe(Class<Event> eventClass, Consumer<EventEnvelope<Event>> handler) {
    handlers.compute(eventClass.getTypeName(), (eventType, consumers) -> {
      if (consumers == null)
        consumers = new ArrayList<>();

      consumers.add((envelope) -> {
        handler.accept(new EventEnvelope<>(eventClass.cast(envelope.data()), envelope.metadata()));
      });

      return consumers;
    });
  }

  private int getCurrentLogPosition() {
    return events.values().stream()
      .mapToInt(List::size)
      .sum();
  }
}
