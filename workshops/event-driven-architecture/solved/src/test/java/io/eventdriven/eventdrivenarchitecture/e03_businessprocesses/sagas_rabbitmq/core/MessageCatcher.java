package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageCatcher {
  public List<Object> published = new CopyOnWriteArrayList<>();

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

  public void shouldReceiveMessages(List<Object> expectedMessages) {
    await()
      .atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        for (var expectedMessage : expectedMessages) {
          boolean found = published.stream().anyMatch(actualMessage ->
            messagesAreEqual(actualMessage, expectedMessage)
          );

          assertThat(found)
            .as("Expected message not found: " + expectedMessage.getClass().getSimpleName() + " = " + expectedMessage
              + "\n" + describe("EXPECTED MESSAGES", expectedMessages)
              + "\n" + describe("ACTUAL MESSAGES", published))
            .isTrue();
        }
      });

    var expectedTypes = expectedMessages.stream().map(Object::getClass).distinct().toList();
    var actualTypes = published.stream().map(Object::getClass).distinct().toList();

    for (var actualType : actualTypes) {
      if (!expectedTypes.contains(actualType)) {
        System.out.println("WARNING: Unexpected message type found: " + actualType.getSimpleName());
      }
    }
  }

  private static String describe(String header, List<Object> messages) {
    var description = new StringBuilder("=== " + header + " (" + messages.size() + ") ===");
    for (int i = 0; i < messages.size(); i++) {
      description.append("\n").append(i).append(": ")
        .append(messages.get(i).getClass().getSimpleName()).append(" = ").append(messages.get(i));
    }
    return description.toString();
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
