package io.eventdriven.distributedprocesses.core.esdb;

import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.Position;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.eventdriven.distributedprocesses.core.messaging.EventHandler;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InMemoryEventStore implements EventStore, InternalEventBus {
  private final Map<String, List<StoredEvent>> streams = new HashMap<>();
  // appending is publishing: the store dispatches through the internal channel
  private final InMemoryEventBus bus = new InMemoryEventBus();

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
    var firstPosition = stream.size();

    stream.addAll(Arrays.stream(events).map(InMemoryEventStore::serialize).toList());

    var result = new AppendResult.Success(revisionOf(streamId), anyPosition);

    bus.publishAt(streamId, firstPosition, events);

    return result;
  }

  @Override
  public <Event> InternalEventBus subscribe(Class<Event> type, Consumer<Event> handler) {
    bus.subscribe(type, handler);

    return this;
  }

  @Override
  public <Event> InternalEventBus subscribeWithMetadata(Class<Event> type, EventHandler<Event> handler) {
    bus.subscribeWithMetadata(type, handler);

    return this;
  }

  @Override
  public InternalEventBus use(Consumer<Object> middleware) {
    bus.use(middleware);

    return this;
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

  private static final Position anyPosition = new Position(0, 0);

  private static final ObjectMapper mapper =
    new JsonMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
}
