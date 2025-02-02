package io.eventdriven.eventstores.mongodb.event_as_document.streams;

import org.bson.types.ObjectId;

public record EventStream(
  ObjectId id,
  String streamName,
  StreamMetadata metadata
) {
}


