package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.util.Optional;

public final class KafkaMessageSerializer {

    public static ProducerRecord<String, String> serialize(String topic, Object message) {
        try {
            String json = MessageSerializer.serialize(message);
            String eventTypeName = EventTypeMapper.toName(message.getClass());
            String correlationId = extractCorrelationId(message);

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, correlationId, json);
            record.headers().add(new RecordHeader("eventType", eventTypeName.getBytes()));

            return record;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message: " + message.getClass().getSimpleName(), e);
        }
    }

    public static Optional<Object> deserialize(ConsumerRecord<String, String> record) {
        try {
            var eventTypeHeader = record.headers().lastHeader("eventType");
            if (eventTypeHeader == null) {
                System.err.println("ERROR: No eventType header found in Kafka message");
                return Optional.empty();
            }

            String eventTypeName = new String(eventTypeHeader.value());
            return MessageSerializer.deserialize(eventTypeName, record.value());
        } catch (Exception e) {
            System.err.println("Failed to deserialize Kafka message: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static String extractCorrelationId(Object message) {
        return message.getClass().getSimpleName();
    }
}
