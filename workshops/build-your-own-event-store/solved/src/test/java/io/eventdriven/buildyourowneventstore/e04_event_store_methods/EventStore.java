package io.eventdriven.buildyourowneventstore.e04_event_store_methods;

import java.time.LocalDateTime;
import java.util.List;

public interface EventStore {
  void init();

  default void appendEvents(
    StreamName streamName,
    Object... events
  ) {
    appendEvents(streamName, null, events);
  }

  void appendEvents(
    StreamName streamName,
    Long expectedVersion,
    Object... events
  );

  default List<Object> getEvents(
    StreamName streamName
  ) {
    return getEvents(streamName, null, null);
  }

  List<Object> getEvents(
    StreamName streamName,
    Long atStreamVersion,
    LocalDateTime atTimestamp
  );
}
