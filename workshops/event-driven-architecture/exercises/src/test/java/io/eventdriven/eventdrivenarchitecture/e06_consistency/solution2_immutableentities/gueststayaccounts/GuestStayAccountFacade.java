package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.BackgroundJobs;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database database;
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database database, MessageBus messageBus, BackgroundJobs backgroundJobs) {
    this.database = database;
    this.collection = database.collection(GuestStayAccount.class);
    this.messageBus = messageBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    collection.store(command.guestStayId(), evolve(INITIAL, checkedIn), 0);
    messageBus.publish(List.of(checkedIn));
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

  private void handle(UUID guestStayId, Function<GuestStayAccount, GuestStayAccountEvent> decide) {
    var event = Retry.onConflict(() -> {
      var loaded = collection.get(guestStayId)
        .orElseThrow(() -> new IllegalStateException("Entity not found"));

      var decided = decide.apply(loaded.document());

      collection.store(guestStayId, evolve(loaded.document(), decided), loaded.version());

      return decided;
    });

    messageBus.publish(List.of(event));
  }
}
