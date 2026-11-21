package io.eventdriven.distributedprocesses.core.messaging;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

class KafkaVersionedMessageConverter {
    private final JacksonJsonDeserializer<Object> jsonDeserializer;
    private final MessageSerializer serializer;

    KafkaVersionedMessageConverter(JacksonJsonDeserializer<Object> jsonDeserializer, MessageSerializer serializer) {
        this.jsonDeserializer = jsonDeserializer;
        this.serializer = serializer;
    }

    Object read(ConsumerRecord<?, ?> record) {
        var typeHeader = record.headers().lastHeader(KafkaMessageBus.TYPE_ID);
        if (typeHeader == null)
            throw new UnreadableMessageException(
                    UnreadableMessageException.UNKNOWN_TYPE, "the record has no type header");

        var typeName = new String(typeHeader.value(), StandardCharsets.UTF_8);

        try {
            var data = (byte[]) record.value();
            if (!serializer.isKnown(typeName)) throw new IllegalStateException("The type is not mapped or trusted");

            var transformed = serializer.tryTransform(typeName, data);
            if (transformed.isPresent()) return transformed.get();

            return jsonDeserializer.deserialize(record.topic(), record.headers(), data);
        } catch (Exception e) {
            throw new UnreadableMessageException(typeName, e);
        }
    }
}
