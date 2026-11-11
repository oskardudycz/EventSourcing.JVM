package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;

public class GroupCheckoutFacade {
  private final Database.Collection<GroupCheckout> collection;
  private final MessageBus messageBus;

  public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
    this.collection = collection;
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var events = Retry.onConflict(() -> {
      if (collection.get(command.groupCheckoutId()).isPresent())
        return List.of();

      var groupCheckout = GroupCheckout.initiate(
        command.groupCheckoutId(),
        command.clerkId(),
        command.guestStayIds(),
        command.now()
      );

      collection.store(command.groupCheckoutId(), groupCheckout, 0);

      return groupCheckout.dequeueUncommittedEvents();
    });

    messageBus.publish(events);
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
    var events = Retry.onConflict(() -> {
      var loaded = collection.get(groupCheckoutId)
        .orElseThrow(() -> new IllegalStateException("Entity not found"));
      var groupCheckout = loaded.document();

      handle.accept(groupCheckout);

      collection.store(groupCheckoutId, groupCheckout, loaded.version());

      return groupCheckout.dequeueUncommittedEvents();
    });

    messageBus.publish(events);
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
