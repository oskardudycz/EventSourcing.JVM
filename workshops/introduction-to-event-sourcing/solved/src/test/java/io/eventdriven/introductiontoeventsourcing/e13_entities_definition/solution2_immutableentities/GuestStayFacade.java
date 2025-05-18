package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities;

import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.Database;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccount;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccount.*;
import static io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayFacade {
  private final Database database;
  private final EventStore eventStore;

  public GuestStayFacade(Database database, EventStore eventStore) {
    this.database = database;
    this.eventStore = eventStore;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    database.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    eventStore.appendToStream(new Object[]{checkedIn});
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    database.store(command.guestStayId(), evolve(account, chargeRecorded));
    eventStore.appendToStream(new Object[]{chargeRecorded});
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    database.store(command.guestStayId(), evolve(account, recordPayment));
    eventStore.appendToStream(new Object[]{recordPayment});
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    database.store(command.guestStayId(), evolve(account, checkedOut));
    eventStore.appendToStream(new Object[]{checkedOut});
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    eventStore.appendToStream(new Object[]{
      new GroupCheckoutEvent.GroupCheckoutInitiated(
        command.groupCheckoutId(),
        command.clerkId(),
        command.guestStayIds(),
        command.now()
      )}
    );
  }

  public sealed interface GroupCheckoutCommand {
    record InitiateGroupCheckout(
      UUID groupCheckoutId,
      UUID clerkId,
      UUID[] guestStayIds,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }
  }
}
