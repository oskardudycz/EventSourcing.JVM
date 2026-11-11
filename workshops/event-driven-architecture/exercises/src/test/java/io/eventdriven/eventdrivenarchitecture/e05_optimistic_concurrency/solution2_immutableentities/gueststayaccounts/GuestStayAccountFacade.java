package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts;

import java.util.UUID;
import java.util.function.Function;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
    this.collection = collection;
    this.messageBus = messageBus;
  }

  public void checkInGuest(CheckInGuest command) {
    if (collection.get(command.guestStayId()).isPresent())
      return;

    var checkedIn = decide(command, INITIAL);

    collection.store(command.guestStayId(), reduce(checkedIn, INITIAL, GuestStayAccount::evolve));
    messageBus.publish(checkedIn);
  }

  public void recordCharge(RecordCharge command) {
    handle(command.guestStayId(), account -> decide(command, account));
  }

  public void recordPayment(RecordPayment command) {
    handle(command.guestStayId(), account -> decide(command, account));
  }

  public void checkOutGuest(CheckOutGuest command) {
    handle(command.guestStayId(), account -> decide(command, account));
  }

  private void handle(UUID guestStayId, Function<GuestStayAccount, List<GuestStayAccountEvent>> decide) {
    var account = collection.get(guestStayId)
      .map(Versioned::document)
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var events = decide.apply(account);

    collection.store(guestStayId, reduce(events, account, GuestStayAccount::evolve));
    messageBus.publish(events);
  }
}
