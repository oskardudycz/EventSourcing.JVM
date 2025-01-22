package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.events;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
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

  public DecodedData encode(
    Object data
  ) {
    @SuppressWarnings("unchecked")
    var codec = (Codec<Object>) codecRegistry.get(data.getClass());

    var bsonDocument = new BsonDocument();
    var writer = new BsonDocumentWriter(bsonDocument);
    codec.encode(writer, data, EncoderContext.builder().isEncodingCollectibleDocument(true).build());

    return new DecodedData(eventTypeMapper.toName(data.getClass()), new Document(bsonDocument));
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
    String eventTypeName,
    Document data
  ) {
    var eventClass = eventTypeMapper.toClass(eventTypeName).get();
    var codec = codecRegistry.get(eventClass);

    var reader = new BsonDocumentReader(data.toBsonDocument(eventClass, codecRegistry));
    return (Event)codec.decode(reader, DecoderContext.builder().build());
  }
}

