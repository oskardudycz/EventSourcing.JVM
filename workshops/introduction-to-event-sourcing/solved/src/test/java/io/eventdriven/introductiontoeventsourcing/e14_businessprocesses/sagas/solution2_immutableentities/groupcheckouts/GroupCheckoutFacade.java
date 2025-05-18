package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution2_immutableentities.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventBus;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.FunctionalTools.FoldLeft.reduce;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckout.INITIAL;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckout.evolve;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.decide;

public class GroupCheckoutFacade {
  private final Database database;
  private final EventBus eventBus;

  public GroupCheckoutFacade(Database database, EventBus eventBus) {
    this.database = database;
    this.eventBus = eventBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var events = decide(command, INITIAL);

    database.store(command.groupCheckoutId(), reduce(events, INITIAL, GroupCheckout::evolve));
    eventBus.publish(events);
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command) {
    final var groupCheckout = database.get(GroupCheckout.class, command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var events = decide(command, groupCheckout);

    database.store(command.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
    eventBus.publish(events);
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command) {
    var groupCheckout = database.get(GroupCheckout.class, command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var events = decide(command, groupCheckout);

    database.store(command.groupCheckoutId(), reduce(events, groupCheckout, GroupCheckout::evolve));
    eventBus.publish(events);
  }
}
