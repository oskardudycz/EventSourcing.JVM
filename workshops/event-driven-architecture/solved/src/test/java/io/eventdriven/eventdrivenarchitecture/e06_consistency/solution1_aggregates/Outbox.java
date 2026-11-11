package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
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

  public record OutboxCheckpoint(long position) {
  }

  public static void storeInOutbox(Database transaction, UUID outboxId, List<?> messages) {
    var outbox = transaction.collection(OutboxMessage.class);

    for (var message : messages)
      outbox.store(UUID.randomUUID(), OutboxMessage.from(outboxId, message));
  }

  public static void publishFromOutbox(Database database, MessageBus messageBus, UUID outboxId) {
    var checkpoints = database.collection(OutboxCheckpoint.class);

    var checkpoint = checkpoints.get(outboxId).map(stored -> stored.document().position()).orElse(0L);

    var messages = database.collection(OutboxMessage.class).getAll().stream()
      .filter(stored -> stored.document().outboxId().equals(outboxId) && stored.position() > checkpoint)
      .toList();

    for (var stored : messages) {
      messageBus.publish(List.of(stored.document().toMessage()));

      Retry.untilSucceeds(() -> checkpoints.store(outboxId, new OutboxCheckpoint(stored.position())));
    }
  }
}
