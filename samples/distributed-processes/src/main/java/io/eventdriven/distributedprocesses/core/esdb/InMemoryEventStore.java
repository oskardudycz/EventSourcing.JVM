package io.eventdriven.distributedprocesses.core.esdb;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.Position;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class InMemoryEventStore implements EventStore {
  private static final Logger logger = LoggerFactory.getLogger(InMemoryEventStore.class);

  private final Map<String, List<StoredEvent>> streams = new HashMap<>();
  private final List<LogEntry> log = new ArrayList<>();
  private final List<Subscription> middlewares = new ArrayList<>();
  private final List<Subscription> subscriptions = new ArrayList<>();
  private boolean dispatching = false;

  @Override
  public ReadResult read(String streamId) {
    var stream = streams.get(streamId);

    if (stream == null)
      return new ReadResult.StreamDoesNotExist();

    if (stream.isEmpty())
      return new ReadResult.NoEventsFound();

    return new ReadResult.Success(stream.stream().map(InMemoryEventStore::deserialize).toArray());
  }

  @Override
  public AppendResult append(String streamId, Object... events) {
    return append(streamId, ExpectedRevision.noStream(), events);
  }

  @Override
  public AppendResult append(String streamId, ExpectedRevision expectedRevision, Object... events) {
    var actualRevision = revisionOf(streamId);

    if (!matches(expectedRevision, actualRevision)) {
      return expectedRevision.equals(ExpectedRevision.noStream())
        ? new AppendResult.StreamAlreadyExists(actualRevision)
        : new AppendResult.Conflict(expectedRevision, actualRevision);
    }

    var stream = streams.computeIfAbsent(streamId, ignored -> new ArrayList<>());

    for (var event : events) {
      var storedEvent = serialize(event);
      log.add(new LogEntry(streamId, stream.size(), storedEvent));
      stream.add(storedEvent);
    }

    var lastLogPosition = log.size() - 1;
    var result = new AppendResult.Success(revisionOf(streamId), new Position(lastLogPosition, lastLogPosition));

    dispatch();

    return result;
  }

  @Override
  public <Event> InMemoryEventStore subscribe(Class<Event> type, Consumer<EventEnvelope<Event>> handler) {
    subscriptions.add(new Subscription(
      type.getTypeName(),
      envelope -> handler.accept(new EventEnvelope<>(type.cast(envelope.data()), envelope.metadata()))
    ));

    return this;
  }

  public InMemoryEventStore use(Consumer<Object> middleware) {
    middlewares.add(new Subscription(null, envelope -> middleware.accept(envelope.data())));

    return this;
  }

  // Appending only writes; delivery runs after the outermost append. So an event appended by a
  // subscriber is delivered after the one it is handling, never in the middle of it. A subscriber
  // that throws keeps its checkpoint, gets nothing more in this run, and gets the same event again
  // on the next append.
  private void dispatch() {
    if (dispatching)
      return;

    dispatching = true;
    var failed = new HashSet<Subscription>();

    try {
      for (var position = firstUndeliveredPosition(); position < log.size(); position++) {
        var entry = log.get(position);

        for (var subscription : Stream.concat(middlewares.stream(), subscriptions.stream()).toList()) {
          if (subscription.checkpoint >= position || failed.contains(subscription))
            continue;

          if (subscription.accepts(entry)) {
            try {
              subscription.handler.accept(toEnvelope(entry, position));
            } catch (RuntimeException e) {
              logger.error(
                "Subscriber failed on {} at log position {}; it will get it again on the next append",
                entry.storedEvent().eventType(), position, e
              );
              failed.add(subscription);
              continue;
            }
          }

          subscription.checkpoint = position;
        }
      }
    } finally {
      dispatching = false;
    }
  }

  private int firstUndeliveredPosition() {
    return Stream.concat(middlewares.stream(), subscriptions.stream())
      .mapToInt(subscription -> subscription.checkpoint + 1)
      .min()
      .orElse(log.size());
  }

  private static EventEnvelope<Object> toEnvelope(LogEntry entry, long logPosition) {
    return new EventEnvelope<>(
      deserialize(entry.storedEvent()),
      new EventMetadata(entry.streamId(), entry.streamPosition(), logPosition)
    );
  }

  private ExpectedRevision revisionOf(String streamId) {
    var stream = streams.getOrDefault(streamId, List.of());

    return stream.isEmpty()
      ? ExpectedRevision.noStream()
      : ExpectedRevision.expectedRevision(stream.size() - 1);
  }

  private static boolean matches(ExpectedRevision expected, ExpectedRevision actual) {
    if (expected.equals(ExpectedRevision.any()) || expected.equals(actual))
      return true;

    return expected.equals(ExpectedRevision.streamExists()) && !actual.equals(ExpectedRevision.noStream());
  }

  private static StoredEvent serialize(Object event) {
    try {
      return new StoredEvent(event.getClass().getTypeName(), mapper.writeValueAsString(event));
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot serialize event of type %s".formatted(event.getClass()), e);
    }
  }

  private static Object deserialize(StoredEvent envelope) {
    try {
      return mapper.readValue(envelope.json(), Class.forName(envelope.eventType()));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot deserialize event of type %s".formatted(envelope.eventType()), e);
    }
  }

  record StoredEvent(String eventType, String json) {
  }

  record LogEntry(String streamId, long streamPosition, StoredEvent storedEvent) {
  }

  private static final class Subscription {
    private final String eventType;
    private final Consumer<EventEnvelope<Object>> handler;
    private int checkpoint = -1;

    private Subscription(String eventType, Consumer<EventEnvelope<Object>> handler) {
      this.eventType = eventType;
      this.handler = handler;
    }

    private boolean accepts(LogEntry entry) {
      return eventType == null || eventType.equals(entry.storedEvent().eventType());
    }
  }

  private static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .changeDefaultVisibility(visibility -> visibility.withFieldVisibility(JsonAutoDetect.Visibility.ANY))
      .build();
}
