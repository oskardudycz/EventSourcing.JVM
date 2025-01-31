package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.streams;

import io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events.EventEnvelope;
import org.bson.types.ObjectId;

import java.util.List;

public record EventStream(
  ObjectId id,
  String streamName,
  List<EventEnvelope> events,
  StreamMetadata metadata
) {
}


