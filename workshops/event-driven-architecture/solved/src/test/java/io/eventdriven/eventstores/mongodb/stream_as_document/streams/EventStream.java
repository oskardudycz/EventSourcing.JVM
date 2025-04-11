package io.eventdriven.eventstores.mongodb.stream_as_document.streams;

import io.eventdriven.eventstores.mongodb.events.EventEnvelope;
import org.bson.types.ObjectId;

import java.util.List;

public record EventStream(
  ObjectId id,
  String streamName,
  List<EventEnvelope> events,
  StreamMetadata metadata
) {
}


