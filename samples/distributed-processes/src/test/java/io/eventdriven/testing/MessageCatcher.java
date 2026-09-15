package io.eventdriven.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageCatcher {
  public List<Object> published = new ArrayList<>();

  public void catchMessage(Object event) {
    published.add(event);
  }

  public void reset() {
    published.clear();
  }

  public void shouldNotReceiveAnyEvent() {
    assertThat(published)
      .as("%s", """
        Expected no messages, but recorded:
        %s""".formatted(Transcript.numbered(published)))
      .isEmpty();
  }

  public <Event> void shouldReceiveSingleEvent(Event event) {
    assertThat(published)
      .as("%s", """
        Expected a single message:
        %s
        Recorded messages:
        %s""".formatted(Transcript.numbered(List.of(event)), Transcript.numbered(published)))
      .hasSize(1);
    assertThat(published).hasOnlyElementsOfTypes(event.getClass()).hasSize(1);
    assertEquals(event, published.getFirst());
  }

  public void shouldReceiveMessages(Object... messages) {
    var expectedMessages = Arrays.asList(messages);

    assertThat(published)
      .as("%s", """
        Expected messages:
        %s
        Recorded messages:
        %s""".formatted(Transcript.numbered(expectedMessages), Transcript.numbered(published)))
      .usingRecursiveComparison()
      .isEqualTo(expectedMessages);
  }
}
