package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageCatcher {
  public List<Object> published = new CopyOnWriteArrayList<>();

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
    assertEquals(event, published.getFirst());
  }

  public void shouldReceiveMessages(List<Object> expectedMessages) {
    for (var expectedMessage : expectedMessages) {
      boolean found = published.stream().anyMatch(actualMessage ->
        messagesAreEqual(actualMessage, expectedMessage)
      );

      if (!found) {
        System.out.println("=== EXPECTED MESSAGES ===");
        for (int i = 0; i < expectedMessages.size(); i++) {
          System.out.println(i + ": " + expectedMessages.get(i).getClass().getSimpleName() + " = " + expectedMessages.get(i));
        }

        System.out.println("=== ACTUAL MESSAGES (" + published.size() + ") ===");
        for (int i = 0; i < published.size(); i++) {
          System.out.println(i + ": " + published.get(i).getClass().getSimpleName() + " = " + published.get(i));
        }

        assertThat(published)
          .as("Expected message not found: " + expectedMessage.getClass().getSimpleName() + " = " + expectedMessage)
          .contains(expectedMessage);
      }
    }

    var expectedTypes = expectedMessages.stream().map(Object::getClass).distinct().toList();
    var actualTypes = published.stream().map(Object::getClass).distinct().toList();

    for (var actualType : actualTypes) {
      if (!expectedTypes.contains(actualType)) {
        System.out.println("WARNING: Unexpected message type found: " + actualType.getSimpleName());
      }
    }
  }

  private boolean messagesAreEqual(Object actual, Object expected) {
    try {
      assertThat(actual)
        .usingRecursiveComparison()
        .isEqualTo(expected);
      return true;
    } catch (AssertionError e) {
      return false;
    }
  }
}
