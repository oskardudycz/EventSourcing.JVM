package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import io.eventdriven.buildyourowneventstore.e01_storage.mongodb.event.EventEnvelope;
import org.bson.types.ObjectId;

import java.util.List;

public record EventStream(
  ObjectId id,
  String streamName,
  List<EventEnvelope> events,
  StreamMetadata metadata
) {
}


