package io.eventdriven.core.messaging.rabbitmq;

import io.eventdriven.core.messaging.UnreadableMessageException;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

class RabbitMQVersionedMessageConverter {
    private final JacksonJsonMessageConverter jsonConverter;
    private final MessageSerializer serializer;

    RabbitMQVersionedMessageConverter(JacksonJsonMessageConverter jsonConverter, MessageSerializer serializer) {
        this.jsonConverter = jsonConverter;
        this.serializer = serializer;
    }

    Object read(Message message) {
        var typeName = message.getMessageProperties().<String>getHeader(RabbitMQMessageBus.TYPE_ID);
        if (typeName == null)
            throw new UnreadableMessageException(
                    UnreadableMessageException.UNKNOWN_TYPE, "the message has no type header");

        try {
            if (!serializer.isKnown(typeName)) throw new IllegalStateException("The type is not mapped or trusted");

            var transformed = serializer.tryTransform(typeName, message.getBody());
            if (transformed.isPresent()) return transformed.get();

            var converted = jsonConverter.fromMessage(message);
            if (converted instanceof byte[]) throw new IllegalStateException("The message is not JSON");

            return converted;
        } catch (Exception e) {
            throw new UnreadableMessageException(typeName, e);
        }
    }
}
