package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.decide;

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
        return List.<GroupCheckoutEvent>of();

      var initiated = decide(command, INITIAL);

      collection.store(command.groupCheckoutId(), reduce(initiated, INITIAL, GroupCheckout::evolve), 0);

      return initiated;
    });

    messageBus.publish(events);
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

      collection.store(groupCheckoutId, reduce(decided, loaded.document(), GroupCheckout::evolve), loaded.version());

      return decided;
    });

    messageBus.publish(events);
  }
}
