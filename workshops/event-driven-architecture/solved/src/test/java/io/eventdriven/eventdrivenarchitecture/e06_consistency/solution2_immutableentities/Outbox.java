package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.TimeoutException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

public final class Outbox {
  private static final JsonMapper mapper =
    JsonMapper.builder()
      .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
      .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
      .build();

  public record OutboxMessage(UUID outboxId, String messageType, String data) {
    static OutboxMessage from(UUID outboxId, Object message) {
      return new OutboxMessage(outboxId, message.getClass().getName(), mapper.writeValueAsString(message));
    }

    Object toMessage() {
      try {
        return mapper.readValue(data, Class.forName(messageType));
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public static void publishOrStoreInOutbox(Database database, MessageBus messageBus, UUID outboxId, List<?> messages) {
    for (var index = 0; index < messages.size(); index++) {
      try {
        messageBus.publish(List.of(messages.get(index)));
      } catch (TimeoutException exception) {
        var notPublished = messages.subList(index, messages.size());

        Retry.untilSucceeds(() -> database.transaction(transaction -> {
          var outbox = transaction.collection(OutboxMessage.class);

          for (var message : notPublished)
            outbox.store(UUID.randomUUID(), OutboxMessage.from(outboxId, message));
        }));
        return;
      }
    }
  }

  public static void publishFromOutbox(Database database, MessageBus messageBus, UUID outboxId) {
    var outbox = database.collection(OutboxMessage.class);

    var messages = outbox.getAll().stream()
      .filter(stored -> stored.document().outboxId().equals(outboxId))
      .toList();

    for (var stored : messages) {
      messageBus.publish(List.of(stored.document().toMessage()));

      Retry.untilSucceeds(() -> outbox.delete(stored.id()));
    }
  }
}
