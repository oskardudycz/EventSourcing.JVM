package io.eventdriven.buildyourowneventstore.e01_storage.mongodb.event;

import org.bson.Document;

public record EventEnvelope(
  String type,
  Document data,
  EventMetadata metadata
) {
  public static <Event> EventEnvelope of(
    final Class<Event> type,
    Event data,
    EventMetadata metadata,
    EventDataCodec codec
  ) {
    var encoded = codec.encode(type, data);
    return new EventEnvelope(encoded.typeName(), encoded.document(), metadata);
  }

  public <Event> Event getEvent(EventDataCodec codec) {
    return codec.decode(type, data);
  }
}
