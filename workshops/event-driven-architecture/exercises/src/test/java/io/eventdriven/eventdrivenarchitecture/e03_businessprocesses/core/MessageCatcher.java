package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    assertEquals(message, published.getFirst());
  }

  public void shouldReceiveMessages(List<Object> messages) {
    assertThat(published)
      .usingRecursiveComparison()
      .isEqualTo(messages);
  }
}
