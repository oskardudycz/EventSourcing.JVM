package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;

public class EventEnvelopeCodec implements Codec<EventEnvelope> {
  private final Codec<Document> documentCodec;
  private final EventDataCodec eventDataCodec;

  public EventEnvelopeCodec(CodecRegistry registry, EventDataCodec eventDataCodec) {
    this.documentCodec = registry.get(Document.class);
    this.eventDataCodec = eventDataCodec;
  }
  @Override
  public EventEnvelope decode(BsonReader bsonReader, DecoderContext decoderContext) {
    var doc = documentCodec.decode(bsonReader, decoderContext);

    var eventType = doc.getString("type");
    var data = doc.get("data", Document.class); // raw fields
    var metadata = doc.get("metadata", EventMetadata.class);

    return new EventEnvelope(eventType, eventDataCodec.decode(eventType, data), metadata);
  }

  @Override
  public void encode(BsonWriter bsonWriter, EventEnvelope eventEnvelope, EncoderContext encoderContext) {
    var doc = new Document();
    // 1) store type
    doc.put("type", eventEnvelope.type());

    // 2) store data as Document by manually converting the record's fields
    var eventData = eventEnvelope.data();
    var dataDoc = eventDataCodec.encode(eventData);
    doc.put("data", dataDoc);
    doc.put("metadata", eventEnvelope.metadata());

    documentCodec.encode(bsonWriter, doc, encoderContext);
  }

  @Override
  public Class<EventEnvelope> getEncoderClass() {
    return EventEnvelope.class;
  }
}
