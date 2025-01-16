package io.eventdriven.buildyourowneventstore.e01_storage.mongodb.event;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;

public class EventEnvelopeCodec implements Codec<EventEnvelope> {
  private final Codec<Document> documentCodec;

  public EventEnvelopeCodec(CodecRegistry registry) {
    this.documentCodec = registry.get(Document.class);
  }
  @Override
  public EventEnvelope decode(BsonReader bsonReader, DecoderContext decoderContext) {
    return null;
  }

  @Override
  public void encode(BsonWriter bsonWriter, EventEnvelope eventEnvelope, EncoderContext encoderContext) {

  }

  @Override
  public Class<EventEnvelope> getEncoderClass() {
    return EventEnvelope.class;
  }
}
