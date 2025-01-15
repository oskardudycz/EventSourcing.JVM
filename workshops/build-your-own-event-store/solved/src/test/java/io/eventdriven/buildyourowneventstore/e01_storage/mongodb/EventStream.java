package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import java.util.List;

public record EventStream<Event>(
  String streamName,
  List<EventEnvelope<Event>> events,
  StreamMetadata metadata
) {
}



