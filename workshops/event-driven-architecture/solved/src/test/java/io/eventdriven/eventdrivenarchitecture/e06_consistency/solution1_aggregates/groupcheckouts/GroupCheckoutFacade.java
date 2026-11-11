package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.BackgroundJobs;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.Outbox;

import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

public class GroupCheckoutFacade {
  private static final UUID OUTBOX_ID = UUID.fromString("7657497b-9b1f-4b52-9cdc-3f2375904cd3");

  private final Database database;

  public GroupCheckoutFacade(Database database, MessageBus messageBus, BackgroundJobs backgroundJobs) {
    this.database = database;

    backgroundJobs.register(() -> Outbox.publishFromOutbox(database, messageBus, OUTBOX_ID));
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    Retry.onConflict(() -> Retry.untilSucceeds(() -> database.transaction(transaction -> {
      var collection = transaction.collection(GroupCheckout.class);

      if (collection.get(command.groupCheckoutId()).isPresent())
        return;

      var groupCheckout = GroupCheckout.initiate(
        command.groupCheckoutId(),
        command.clerkId(),
        command.guestStayIds(),
        command.now()
      );

      collection.store(command.groupCheckoutId(), groupCheckout, 0);

      Outbox.storeInOutbox(transaction, OUTBOX_ID, groupCheckout.dequeueUncommittedEvents());
    })));
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
    handle(command.groupCheckoutId(), groupCheckout ->
      groupCheckout.recordGuestCheckoutCompletion(command.guestStayId(), command.completedAt())
    );
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
    handle(command.groupCheckoutId(), groupCheckout ->
      groupCheckout.recordGuestCheckoutFailure(command.guestStayId(), command.failedAt())
    );
  }

  private void handle(UUID groupCheckoutId, Consumer<GroupCheckout> handle) {
    Retry.onConflict(() -> Retry.untilSucceeds(() -> database.transaction(transaction -> {
      var collection = transaction.collection(GroupCheckout.class);
      var loaded = collection.get(groupCheckoutId)
        .orElseThrow(() -> new IllegalStateException("Entity not found"));
      var groupCheckout = loaded.document();

      handle.accept(groupCheckout);

      collection.store(groupCheckoutId, groupCheckout, loaded.version());

      Outbox.storeInOutbox(transaction, OUTBOX_ID, groupCheckout.dequeueUncommittedEvents());
    })));
  }

  public sealed interface GroupCheckoutCommand {
    record InitiateGroupCheckout(
      UUID groupCheckoutId,
      UUID clerkId,
      UUID[] guestStayIds,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }

    record RecordGuestCheckoutCompletion(
      UUID groupCheckoutId,
      UUID guestStayId,
      OffsetDateTime completedAt
    ) implements GroupCheckoutCommand {
    }

    record RecordGuestCheckoutFailure(
      UUID groupCheckoutId,
      UUID guestStayId,
      OffsetDateTime failedAt
    ) implements GroupCheckoutCommand {
    }
  }
}
