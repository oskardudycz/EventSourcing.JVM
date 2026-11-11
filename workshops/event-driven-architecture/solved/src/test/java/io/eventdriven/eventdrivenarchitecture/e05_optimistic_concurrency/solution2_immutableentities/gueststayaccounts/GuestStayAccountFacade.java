package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;
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
    var events = Retry.onConflict(() -> {
      if (collection.get(command.guestStayId()).isPresent())
        return List.<GuestStayAccountEvent>of();

      var checkedIn = decide(command, INITIAL);

      collection.store(command.guestStayId(), reduce(checkedIn, INITIAL, GuestStayAccount::evolve), 0);

      return checkedIn;
    });

    messageBus.publish(events);
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
    var events = Retry.onConflict(() -> {
      var loaded = collection.get(guestStayId)
        .orElseThrow(() -> new IllegalStateException("Entity not found"));

      var decided = decide.apply(loaded.document());

      collection.store(guestStayId, reduce(decided, loaded.document(), GuestStayAccount::evolve), loaded.version());

      return decided;
    });

    messageBus.publish(events);
  }
}
