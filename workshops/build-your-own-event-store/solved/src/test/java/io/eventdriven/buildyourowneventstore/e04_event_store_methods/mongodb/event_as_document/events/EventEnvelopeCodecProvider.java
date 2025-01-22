package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.event_as_document.events;

import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

public class EventEnvelopeCodecProvider implements CodecProvider {
  @Override
  @SuppressWarnings("unchecked")
  public <T> Codec<T> get(Class<T> clazz, CodecRegistry codecRegistry) {
    if (clazz == EventEnvelope.class) {
      return (Codec<T>) new EventEnvelopeCodec(codecRegistry, new EventDataCodec(codecRegistry, EventTypeMapper.DEFAULT));
    }

    return null;
  }
}
