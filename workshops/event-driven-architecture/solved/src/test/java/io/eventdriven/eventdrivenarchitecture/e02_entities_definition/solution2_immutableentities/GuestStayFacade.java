package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.Database;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccount;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccount.*;
import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.decide;

public class GuestStayFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
    this.collection = collection;
    this.messageBus = messageBus;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var checkedIn = decide(command, INITIAL);

    collection.store(command.guestStayId(), evolve(INITIAL, checkedIn));
    messageBus.publish(List.of(checkedIn));
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var chargeRecorded = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, chargeRecorded));
    messageBus.publish(List.of(chargeRecorded));
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var recordPayment = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, recordPayment));
    messageBus.publish(List.of(recordPayment));
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    var checkedOut = decide(command, account);

    collection.store(command.guestStayId(), evolve(account, checkedOut));
    messageBus.publish(List.of(checkedOut));
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    messageBus.publish(List.of(
      new GroupCheckoutEvent.GroupCheckoutInitiated(
        command.groupCheckoutId(),
        command.clerkId(),
        command.guestStayIds(),
        command.now()
      )
    ));
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
