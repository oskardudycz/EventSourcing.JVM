package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database database;
  private final EventBus eventBus;

  public GuestStayAccountFacade(Database database, EventBus eventBus) {
    this.database = database;
    this.eventBus = eventBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    database.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    eventBus.publish(new Object[]{checkedIn});
  }

  public void recordCharge(RecordCharge command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    database.store(command.guestStayId(), evolve(account, chargeRecorded));
    eventBus.publish(new Object[]{chargeRecorded});
  }

  public void recordPayment(RecordPayment command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    database.store(command.guestStayId(), evolve(account, recordPayment));
    eventBus.publish(new Object[]{recordPayment});
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    database.store(command.guestStayId(), evolve(account, checkedOut));
    eventBus.publish(new Object[]{checkedOut});
  }
}
