package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final EventBus eventBus;

  public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, EventBus eventBus) {
    this.collection = collection;
    this.eventBus = eventBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    collection.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    eventBus.publish(List.of(checkedIn));
  }

  public void recordCharge(RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, chargeRecorded));
    eventBus.publish(List.of(chargeRecorded));
  }

  public void recordPayment(RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, recordPayment));
    eventBus.publish(List.of(recordPayment));
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, checkedOut));
    eventBus.publish(List.of(checkedOut));
  }
}
