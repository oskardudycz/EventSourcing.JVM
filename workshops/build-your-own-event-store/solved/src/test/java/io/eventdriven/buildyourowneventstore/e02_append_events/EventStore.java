package io.eventdriven.buildyourowneventstore.e02_append_events;

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
    Long expectedStreamPosition,
    Object... events
  );
}
