package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageCatcher {
  public List<Object> published = new CopyOnWriteArrayList<>();

  public void catchMessage(Object message) {
    published.add(message);
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
    assertEquals(event, published.getFirst());
  }

  public void shouldReceiveMessages(Object[] messages) {
    assertThat(published)
      .usingRecursiveComparison()
      .isEqualTo(Arrays.asList(messages));
  }
}
