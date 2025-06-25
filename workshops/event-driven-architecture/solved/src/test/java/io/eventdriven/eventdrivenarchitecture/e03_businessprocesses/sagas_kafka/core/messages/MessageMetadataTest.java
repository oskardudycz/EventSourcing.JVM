package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;

import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MessageMetadataTest {

    @Test
    public void shouldExtractMetadataFromSpringMessage() {
        UUID messageId = UUID.randomUUID();
        Message<String> message = MessageBuilder
            .withPayload("{\"test\":\"data\"}")
            .setHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, "TestEvent")
            .setHeader(CORRELATION_ID, "corr-456")
            .setHeader(CAUSATION_ID, "cause-789")
            .setHeader(ENTITY_ID, "entity-111")
            .setHeader(ENTITY_VERSION, 5L)
            .build();

        assertThat(getMessageId(message.getHeaders())).isNotNull();
        assertThat(getMessageType(message.getHeaders())).hasValue("TestEvent");
        assertThat(getCorrelationId(message.getHeaders())).hasValue("corr-456");
        assertThat(getCausationId(message.getHeaders())).hasValue("cause-789");
        assertThat(getEntityId(message.getHeaders())).hasValue("entity-111");
        assertThat(getEntityVersion(message.getHeaders())).hasValue(5L);
    }

    @Test
    public void shouldHandleMissingOptionalFields() {
        Message<String> message = MessageBuilder
            .withPayload("{\"test\":\"data\"}")
            .setHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, "TestEvent")
            .setHeader(CORRELATION_ID, "corr-456")
            .build();

        assertThat(getMessageId(message.getHeaders())).isNotNull();
        assertThat(getCausationId(message.getHeaders())).isEmpty();
        assertThat(getEntityId(message.getHeaders())).isEmpty();
        assertThat(getEntityVersion(message.getHeaders())).isEmpty();
    }
}
