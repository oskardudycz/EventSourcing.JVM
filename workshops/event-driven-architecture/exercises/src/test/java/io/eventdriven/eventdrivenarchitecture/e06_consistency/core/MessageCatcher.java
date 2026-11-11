package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageCatcher {
  public List<Object> published = new ArrayList<>();

  public synchronized void catchMessage(Object message) {
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
    assertThat(published.getFirst()).usingRecursiveComparison().isEqualTo(message);
  }

  public void shouldReceiveMessages(List<Object> messages) {
    assertThat(published)
      .usingRecursiveComparison()
      .isEqualTo(messages);
  }

  public void shouldReceiveMessagesInAnyOrder(List<Object> messages) {
    assertThat(published)
      .usingRecursiveFieldByFieldElementComparator()
      .containsExactlyInAnyOrderElementsOf(messages);
  }
}
