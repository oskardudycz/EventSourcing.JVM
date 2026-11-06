package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckInGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordCharge;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordPayment;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database collection, MessageBus messageBus) {
    this.collection = collection.collection(GuestStayAccount.class);
    this.messageBus = messageBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    collection.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    messageBus.publish(List.of(checkedIn));
  }

  public void recordCharge(RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, chargeRecorded));
    messageBus.publish(List.of(chargeRecorded));
  }

  public void recordPayment(RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, recordPayment));
    messageBus.publish(List.of(recordPayment));
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, checkedOut));
    messageBus.publish(List.of(checkedOut));
  }
}
