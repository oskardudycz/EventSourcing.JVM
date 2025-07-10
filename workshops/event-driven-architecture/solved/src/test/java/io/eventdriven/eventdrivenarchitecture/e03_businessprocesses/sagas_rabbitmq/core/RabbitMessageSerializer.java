package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import java.util.Optional;

public final class RabbitMessageSerializer {

    public static Message serialize(Object message) {
        try {
            String json = MessageSerializer.serialize(message);
            String eventTypeName = EventTypeMapper.toName(message.getClass());
            
            MessageProperties properties = new MessageProperties();
            properties.getHeaders().put("eventType", eventTypeName);
            properties.setContentType("application/json");
            
            return MessageBuilder
                .withBody(json.getBytes())
                .andProperties(properties)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message: " + message.getClass().getSimpleName(), e);
        }
    }

    public static Optional<Object> deserialize(Message rawMessage) {
        try {
            String eventTypeName = (String) rawMessage.getMessageProperties().getHeaders().get("eventType");
            if (eventTypeName == null) {
                System.err.println("ERROR: No eventType header found in message");
                return Optional.empty();
            }

            String json = new String(rawMessage.getBody());
            return MessageSerializer.deserialize(eventTypeName, json);
        } catch (Exception e) {
            System.err.println("Failed to deserialize RabbitMQ message: " + e.getMessage());
            return Optional.empty();
        }
    }
}