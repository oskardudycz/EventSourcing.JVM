package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;

public record EventEnvelope(
  String type,
  Document data,
  EventMetadata metadata
) {
  public static <Event> EventEnvelope of(
    final Class<Event> type,
    Event data,
    EventMetadata metadata,
    CodecRegistry codecRegistry
  ) {
    var codec = codecRegistry.get(type);

    BsonDocument bsonDocument = new BsonDocument();
    BsonDocumentWriter writer = new BsonDocumentWriter(bsonDocument);
    codec.encode(writer, data, EncoderContext.builder().isEncodingCollectibleDocument(true).build());

    return new EventEnvelope(data.getClass().getTypeName(), new Document(bsonDocument), metadata);
  }

  public <Event> Event getEvent(CodecRegistry codecRegistry) {
    try {

      var codec = codecRegistry.get(Class.forName(type()));

      BsonDocumentReader reader = new BsonDocumentReader(data().toBsonDocument(Class.forName(type()), codecRegistry));
      return (Event) codec.decode(reader, DecoderContext.builder().build());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
