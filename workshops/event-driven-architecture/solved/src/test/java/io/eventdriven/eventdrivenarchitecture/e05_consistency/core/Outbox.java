package io.eventdriven.eventdrivenarchitecture.e05_consistency.core;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class Outbox {
  public record OutboxMessage(UUID id, Object message) {
  }

  public static void storeAndPublish(
    Database database,
    MessageBus messageBus,
    List<?> messages,
    Consumer<Database> storeState
  ) {
    var outboxMessages = messages.stream()
      .map(message -> new OutboxMessage(UUID.randomUUID(), message))
      .toList();

    Retry.untilSucceeds(() -> database.transaction(transaction -> {
      storeState.accept(transaction);

      var outbox = transaction.collection(OutboxMessage.class);
      for (var outboxMessage : outboxMessages)
        outbox.store(outboxMessage.id(), outboxMessage);
    }));

    var outbox = database.collection(OutboxMessage.class);
    for (var outboxMessage : outboxMessages) {
      Retry.untilSucceeds(() -> messageBus.publish(List.of(outboxMessage.message())));
      Retry.untilSucceeds(() -> outbox.delete(outboxMessage.id()));
    }
  }

  public static void publish(Database database, MessageBus messageBus, List<?> messages) {
    storeAndPublish(database, messageBus, messages, transaction -> {
    });
  }
}
