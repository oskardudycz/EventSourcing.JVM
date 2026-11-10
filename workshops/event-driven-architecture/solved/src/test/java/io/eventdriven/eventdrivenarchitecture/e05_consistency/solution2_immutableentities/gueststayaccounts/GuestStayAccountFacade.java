package io.eventdriven.eventdrivenarchitecture.e05_consistency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.Outbox;
import io.eventdriven.eventdrivenarchitecture.e05_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e05_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e05_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e05_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database database;
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database database, MessageBus messageBus) {
    this.database = database;
    this.collection = database.collection(GuestStayAccount.class);
    this.messageBus = messageBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    Outbox.storeAndPublish(database, messageBus, List.of(checkedIn),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), evolve(INITIAL, checkedIn)));
  }

  public void recordCharge(RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    Outbox.storeAndPublish(database, messageBus, List.of(chargeRecorded),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), evolve(account, chargeRecorded)));
  }

  public void recordPayment(RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    Outbox.storeAndPublish(database, messageBus, List.of(recordPayment),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), evolve(account, recordPayment)));
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    Outbox.storeAndPublish(database, messageBus, List.of(checkedOut),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), evolve(account, checkedOut)));
  }
}
