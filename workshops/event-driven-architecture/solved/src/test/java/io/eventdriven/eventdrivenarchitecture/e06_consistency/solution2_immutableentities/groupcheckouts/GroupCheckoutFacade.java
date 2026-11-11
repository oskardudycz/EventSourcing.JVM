package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.BackgroundJobs;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.Outbox;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.eventdrivenarchitecture.e06_consistency.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.decide;

public class GroupCheckoutFacade {
  private static final UUID OUTBOX_ID = UUID.fromString("223a7279-a751-473c-b2f9-686428a7bdc6");

  private final Database database;
  private final Database.Collection<GroupCheckout> collection;
  private final MessageBus messageBus;

  public GroupCheckoutFacade(Database database, MessageBus messageBus, BackgroundJobs backgroundJobs) {
    this.database = database;
    this.collection = database.collection(GroupCheckout.class);
    this.messageBus = messageBus;

    backgroundJobs.register(() -> Outbox.publishFromOutbox(database, messageBus, OUTBOX_ID));
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var events = Retry.onConflict(() -> {
      if (collection.get(command.groupCheckoutId()).isPresent())
        return List.<GroupCheckoutEvent>of();

      var initiated = decide(command, INITIAL);

      Retry.untilSucceeds(() -> collection.store(command.groupCheckoutId(), reduce(initiated, INITIAL, GroupCheckout::evolve), 0));

      return initiated;
    });

    Outbox.publishOrStoreInOutbox(database, messageBus, OUTBOX_ID, events);
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
    handle(command.groupCheckoutId(), groupCheckout -> decide(command, groupCheckout));
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
    handle(command.groupCheckoutId(), groupCheckout -> decide(command, groupCheckout));
  }

  private void handle(UUID groupCheckoutId, Function<GroupCheckout, List<GroupCheckoutEvent>> decide) {
    var events = Retry.onConflict(() -> {
      var loaded = collection.get(groupCheckoutId)
        .orElseThrow(() -> new IllegalStateException("Entity not found"));

      var decided = decide.apply(loaded.document());

      Retry.untilSucceeds(() -> collection.store(groupCheckoutId, reduce(decided, loaded.document(), GroupCheckout::evolve), loaded.version()));

      return decided;
    });

    Outbox.publishOrStoreInOutbox(database, messageBus, OUTBOX_ID, events);
  }
}
