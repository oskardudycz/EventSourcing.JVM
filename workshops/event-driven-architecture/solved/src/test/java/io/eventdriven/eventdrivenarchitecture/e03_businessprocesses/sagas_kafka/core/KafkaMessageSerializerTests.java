package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class KafkaMessageSerializerTests {

    public record TestEvent(UUID id, String name, OffsetDateTime timestamp) {}

    public record TestCommand(UUID entityId, String action, double amount) {}

    @Test
    void shouldSerializeRecord() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());
        String topic = "test-topic";

        // When
        ProducerRecord<String, String> producerRecord = KafkaMessageSerializer.serialize(topic, testEvent);

        // Then
        assertNotNull(producerRecord, "ProducerRecord should not be null");
        assertEquals(topic, producerRecord.topic(), "Topic should match");
        assertEquals("TestEvent", producerRecord.key(), "Key should be class name");
        assertNotNull(producerRecord.value(), "JSON value should not be null");

        // Verify JSON contains expected data
        String json = producerRecord.value();
        assertTrue(json.contains(testEvent.id().toString()), "JSON should contain UUID");
        assertTrue(json.contains(testEvent.name()), "JSON should contain name");
    }

    @Test
    void shouldSerializeCommand() {
        // Given
        var testCommand = new TestCommand(UUID.randomUUID(), "checkout", 150.50);
        String topic = "test-topic";

        // When
        ProducerRecord<String, String> producerRecord = KafkaMessageSerializer.serialize(topic, testCommand);

        // Then
        assertNotNull(producerRecord, "ProducerRecord should not be null");
        assertEquals(topic, producerRecord.topic(), "Topic should match");
        assertEquals("TestCommand", producerRecord.key(), "Key should be class name");
        assertNotNull(producerRecord.value(), "JSON value should not be null");

        // Verify JSON contains expected data
        String json = producerRecord.value();
        assertTrue(json.contains(testCommand.entityId().toString()), "JSON should contain entity ID");
        assertTrue(json.contains(testCommand.action()), "JSON should contain action");
        assertTrue(json.contains("150.5"), "JSON should contain amount");
    }

    @Test
    void shouldIncludeEventTypeHeader() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());
        String topic = "test-topic";

        // When
        ProducerRecord<String, String> producerRecord = KafkaMessageSerializer.serialize(topic, testEvent);

        // Then
        var eventTypeHeader = producerRecord.headers().lastHeader("eventType");
        assertNotNull(eventTypeHeader, "EventType header should be present");

        String eventType = new String(eventTypeHeader.value());
        assertTrue(eventType.contains("TestEvent"), "EventType should contain class name");
    }

    @Test
    void shouldSetTopicCorrectly() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());
        String topic = "hotel-financial";

        // When
        ProducerRecord<String, String> producerRecord = KafkaMessageSerializer.serialize(topic, testEvent);

        // Then
        assertEquals(topic, producerRecord.topic());
    }

    @Test
    void shouldUseClassNameAsCorrelationId() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());
        String topic = "test-topic";

        // When
        ProducerRecord<String, String> producerRecord = KafkaMessageSerializer.serialize(topic, testEvent);

        // Then
        assertEquals("TestEvent", producerRecord.key());
    }

    @Test
    void shouldRoundTripSerializeDeserializeViaMessageSerializer() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());

        // When - test the underlying serialization directly
        String json = MessageSerializer.serialize(testEvent);
        String eventTypeName = EventTypeMapper.toName(testEvent.getClass());
        var deserializedOptional = MessageSerializer.deserialize(eventTypeName, json);

        // Then
        assertTrue(deserializedOptional.isPresent(), "Deserialization should succeed");

        var deserialized = (TestEvent) deserializedOptional.get();
        assertEquals(testEvent.id(), deserialized.id());
        assertEquals(testEvent.name(), deserialized.name());
        assertEquals(testEvent.timestamp(), deserialized.timestamp());
    }

    @Test
    void shouldHandleSerializationFailure() {
        Object problematicObject = null;

        // When/Then
        assertThrows(RuntimeException.class, () -> {
            KafkaMessageSerializer.serialize("test-topic", problematicObject);
        }, "Should throw RuntimeException for problematic objects");
    }
}
