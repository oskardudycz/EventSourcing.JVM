package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import org.springframework.stereotype.Service;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.decide;

@Service
public class GroupCheckoutFacade {
  private final Database.Collection<GroupCheckout> collection;
  private final MessageBus messageBus;

  public GroupCheckoutFacade(Database database, MessageBus messageBus) {
    this.collection = database.collection(GroupCheckout.class);
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var events = decide(command, INITIAL);

    collection.store(command.groupCheckoutId(), reduce(events, INITIAL, GroupCheckout::evolve));
    messageBus.publish(events);
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
    final var groupCheckout = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var events = decide(command, groupCheckout);

    collection.store(command.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
    messageBus.publish(events);
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
    var groupCheckout = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var events = decide(command, groupCheckout);

    collection.store(command.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
    messageBus.publish(events);
  }
}
