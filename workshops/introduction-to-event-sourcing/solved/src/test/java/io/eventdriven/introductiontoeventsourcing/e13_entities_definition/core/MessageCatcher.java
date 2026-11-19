package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageCatcher {
    public List<Object> published = new ArrayList<>();

    public void catchMessage(Object event) {
        published.add(event);
    }

    public void reset() {
        published.clear();
    }

    public void shouldNotReceiveAnyEvent() {
        assertThat(published).isEmpty();
    }

    public <Event> void shouldReceiveSingleEvent(Event event) {
        assertThat(published).hasSize(1);
        assertThat(published).hasOnlyElementsOfTypes(event.getClass()).hasSize(1);
        assertThat(published.getFirst()).isEqualTo(event);
    }

    public void shouldReceiveMessages(Object[] messages) {
        assertThat(published).usingRecursiveComparison().isEqualTo(Arrays.asList(messages));
    }
}
