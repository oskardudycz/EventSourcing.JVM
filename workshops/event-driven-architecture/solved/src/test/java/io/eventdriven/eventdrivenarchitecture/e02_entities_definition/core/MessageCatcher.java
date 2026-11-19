package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

public class MessageCatcher {
    public List<Object> published = new ArrayList<>();

    public void catchMessage(Object message) {
        published.add(message);
    }

    public void reset() {
        published.clear();
    }

    public void shouldNotReceiveAnyMessage() {
        assertThat(published).isEmpty();
    }

    public <Message> void shouldReceiveSingleMessage(Message message) {
        assertThat(published).hasSize(1);
        assertThat(published).hasOnlyElementsOfTypes(message.getClass()).hasSize(1);
        assertThat(published.getFirst()).isEqualTo(message);
    }

    public void shouldReceiveMessages(List<Object> messages) {
        assertThat(published).usingRecursiveComparison().isEqualTo(messages);
    }
}
