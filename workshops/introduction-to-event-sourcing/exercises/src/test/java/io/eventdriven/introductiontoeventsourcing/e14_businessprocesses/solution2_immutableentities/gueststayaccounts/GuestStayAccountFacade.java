package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.INITIAL;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.evolve;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayAccountFacade {
  private final EventStore eventStore;

  public GuestStayAccountFacade(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public void checkInGuest(CheckInGuest command) {
    var account = getAccount(command.guestStayId());

    var checkedIn = decide(command, account);

    eventStore.appendToStream(command.guestStayId().toString(), new Object[]{checkedIn});
  }

  public void recordCharge(RecordCharge command) {
    var account = getAccount(command.guestStayId());

    var chargeRecorded = decide(command, account);

    eventStore.appendToStream(command.guestStayId().toString(), new Object[]{chargeRecorded});
  }

  public void recordPayment(RecordPayment command) {
    var account = getAccount(command.guestStayId());

    var recordPayment = decide(command, account);

    eventStore.appendToStream(command.guestStayId().toString(), new Object[]{recordPayment});
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = getAccount(command.guestStayId());

    var checkedOut = decide(command, account);

    eventStore.appendToStream(command.guestStayId().toString(), new Object[]{checkedOut});
  }

  private GuestStayAccount getAccount(UUID accountId) {
    return eventStore.aggregateStream(
      accountId.toString(),
      GuestStayAccount::evolve,
      () -> GuestStayAccount.INITIAL
    );
  }
}
