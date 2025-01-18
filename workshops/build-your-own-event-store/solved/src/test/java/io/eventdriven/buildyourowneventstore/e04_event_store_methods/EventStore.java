package io.eventdriven.buildyourowneventstore.e04_event_store_methods;

import java.time.LocalDateTime;
import java.util.List;

public interface EventStore {
  void init();

  default <Stream> void appendEvents(
    Class<Stream> streamClass,
    String streamId,
    Object... events
  ) {
    appendEvents(streamClass, streamId, null, events);
  }

  <Stream> void appendEvents(
    Class<Stream> streamClass,
    String streamId,
    Long expectedVersion,
    Object... events
  );

  default <Stream>  List<Object> getEvents(
    Class<Stream> streamClass,
    String streamId
  ) {
    return getEvents(streamClass, streamId, null, null);
  }

  <Stream> List<Object> getEvents(
    Class<Stream> streamClass,
    String streamId,
    Long atStreamVersion,
    LocalDateTime atTimestamp
  );
}
