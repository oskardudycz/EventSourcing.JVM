package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;

public class EventDataCodec {
  private final CodecRegistry codecRegistry;
  private final EventTypeMapper eventTypeMapper;

  public EventDataCodec(CodecRegistry codecRegistry, EventTypeMapper eventTypeMapper) {
    this.codecRegistry = codecRegistry;
    this.eventTypeMapper = eventTypeMapper;
  }

  public record DecodedData(String typeName, Document document) {
  }

  public <Event> DecodedData encode(
    final Class<Event> type,
    Event data
  ) {
    var codec = codecRegistry.get(type);

    var bsonDocument = new BsonDocument();
    var writer = new BsonDocumentWriter(bsonDocument);
    codec.encode(writer, data, EncoderContext.builder().isEncodingCollectibleDocument(true).build());

    return new DecodedData(eventTypeMapper.toName(data.getClass()), new Document(bsonDocument));
  }

  public <Event> Event decode(
    final Class<Event> type,
    String eventTypeName,
    Document data
  ) {
    //var eventClass = eventTypeMapper.toClass(eventTypeName);
    var codec = codecRegistry.get(type);

    var reader = new BsonDocumentReader(data.toBsonDocument(type, codecRegistry));
    return codec.decode(reader, DecoderContext.builder().build());
  }
}

