package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final Database database;
  private final EventStore eventStore;

  public GuestStayAccountFacade(Database database, EventStore eventStore) {
    this.database = database;
    this.eventStore = eventStore;
  }

  public void checkInGuest(CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    database.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    eventStore.appendToStream(new Object[]{checkedIn});
  }

  public void recordCharge(RecordCharge command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    database.store(command.guestStayId(), evolve(account, chargeRecorded));
    eventStore.appendToStream(new Object[]{chargeRecorded});
  }

  public void recordPayment(RecordPayment command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    database.store(command.guestStayId(), evolve(account, recordPayment));
    eventStore.appendToStream(new Object[]{recordPayment});
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    database.store(command.guestStayId(), evolve(account, checkedOut));
    eventStore.appendToStream(new Object[]{checkedOut});
  }
}
