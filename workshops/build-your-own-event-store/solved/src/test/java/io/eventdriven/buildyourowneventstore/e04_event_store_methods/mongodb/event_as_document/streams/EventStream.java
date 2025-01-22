package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.streams;

import org.bson.types.ObjectId;

public record EventStream(
  ObjectId id,
  String streamName,
  StreamMetadata metadata
) {
}


