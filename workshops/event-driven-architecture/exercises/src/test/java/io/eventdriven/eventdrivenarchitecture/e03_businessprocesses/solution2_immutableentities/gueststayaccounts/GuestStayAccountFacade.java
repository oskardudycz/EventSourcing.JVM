package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database.Collection<GuestStayAccount> database;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database database, MessageBus messageBus) {
    this.database = database.collection(GuestStayAccount.class);
    this.messageBus = messageBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    database.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    messageBus.publish(List.of(checkedIn));
  }

  public void recordCharge(RecordCharge command) {
    var account = database.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    database.store(command.guestStayId(), evolve(account, chargeRecorded));
    messageBus.publish(List.of(chargeRecorded));
  }

  public void recordPayment(RecordPayment command) {
    var account = database.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    database.store(command.guestStayId(), evolve(account, recordPayment));
    messageBus.publish(List.of(recordPayment));
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = database.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    database.store(command.guestStayId(), evolve(account, checkedOut));
    messageBus.publish(List.of(checkedOut));
  }
}
