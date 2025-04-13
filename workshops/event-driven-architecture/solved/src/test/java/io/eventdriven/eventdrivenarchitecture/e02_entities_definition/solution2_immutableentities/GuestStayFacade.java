package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.Database;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccount;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccount.*;
import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayFacade {
  private final Database database;
  private final EventBus eventBus;

  public GuestStayFacade(Database database, EventBus eventBus) {
    this.database = database;
    this.eventBus = eventBus;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    database.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    eventBus.publish(new Object[]{checkedIn});
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    database.store(command.guestStayId(), evolve(account, chargeRecorded));
    eventBus.publish(new Object[]{chargeRecorded});
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    database.store(command.guestStayId(), evolve(account, recordPayment));
    eventBus.publish(new Object[]{recordPayment});
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    var account = database.get(GuestStayAccount.class, command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    database.store(command.guestStayId(), evolve(account, checkedOut));
    eventBus.publish(new Object[]{checkedOut});
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    eventBus.publish(new Object[]{
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
