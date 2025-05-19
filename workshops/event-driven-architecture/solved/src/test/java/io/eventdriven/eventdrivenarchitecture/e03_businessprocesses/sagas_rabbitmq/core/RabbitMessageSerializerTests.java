package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.amqp.core.Message;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RabbitMessageSerializerTests {

    public record TestEvent(UUID id, String name, OffsetDateTime timestamp) {}
    
    public record TestCommand(UUID entityId, String action, double amount) {}

    @Test
    void shouldSerializeAndDeserializeSimpleRecord() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());

        // When
        Message serializedMessage = RabbitMessageSerializer.serialize(testEvent);
        var deserializedOptional = RabbitMessageSerializer.deserialize(serializedMessage);

        // Then
        assertTrue(deserializedOptional.isPresent(), "Deserialization should succeed");
        
        var deserialized = (TestEvent) deserializedOptional.get();
        assertEquals(testEvent.id(), deserialized.id());
        assertEquals(testEvent.name(), deserialized.name());
        assertEquals(testEvent.timestamp(), deserialized.timestamp());
    }

    @Test
    void shouldSerializeAndDeserializeCommand() {
        // Given
        var testCommand = new TestCommand(UUID.randomUUID(), "checkout", 150.50);

        // When
        Message serializedMessage = RabbitMessageSerializer.serialize(testCommand);
        var deserializedOptional = RabbitMessageSerializer.deserialize(serializedMessage);

        // Then
        assertTrue(deserializedOptional.isPresent(), "Deserialization should succeed");
        
        var deserialized = (TestCommand) deserializedOptional.get();
        assertEquals(testCommand.entityId(), deserialized.entityId());
        assertEquals(testCommand.action(), deserialized.action());
        assertEquals(testCommand.amount(), deserialized.amount());
    }

    @Test
    void shouldIncludeEventTypeHeader() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());

        // When
        Message serializedMessage = RabbitMessageSerializer.serialize(testEvent);

        // Then
        var eventType = serializedMessage.getMessageProperties().getHeaders().get("eventType");
        assertNotNull(eventType, "EventType header should be present");
        assertTrue(eventType.toString().contains("TestEvent"), "EventType should contain class name");
    }

    @Test
    void shouldSetContentTypeToJson() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());

        // When
        Message serializedMessage = RabbitMessageSerializer.serialize(testEvent);

        // Then
        assertEquals("application/json", serializedMessage.getMessageProperties().getContentType());
    }

    @Test
    void shouldHandleMissingEventTypeHeader() {
        // Given
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());
        Message serializedMessage = RabbitMessageSerializer.serialize(testEvent);
        
        // Remove the eventType header to simulate missing header
        serializedMessage.getMessageProperties().getHeaders().remove("eventType");

        // When
        var deserializedOptional = RabbitMessageSerializer.deserialize(serializedMessage);

        // Then
        assertTrue(deserializedOptional.isEmpty(), "Deserialization should fail without eventType header");
    }

    @Test
    void shouldHandleCorruptedJson() {
        // Given - create a message with invalid JSON
        var testEvent = new TestEvent(UUID.randomUUID(), "test-event", OffsetDateTime.now());
        Message originalMessage = RabbitMessageSerializer.serialize(testEvent);
        
        // Corrupt the JSON body
        Message corruptedMessage = new Message("{invalid-json".getBytes(), originalMessage.getMessageProperties());

        // When
        var deserializedOptional = RabbitMessageSerializer.deserialize(corruptedMessage);

        // Then
        assertTrue(deserializedOptional.isEmpty(), "Deserialization should fail with corrupted JSON");
    }
}