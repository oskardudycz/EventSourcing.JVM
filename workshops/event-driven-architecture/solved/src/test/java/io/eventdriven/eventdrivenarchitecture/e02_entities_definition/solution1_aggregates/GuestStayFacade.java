package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.Database;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccount;

import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccountCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.groupcheckouts.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.*;

public class GuestStayFacade {
  private final Database database;
  private final EventBus eventBus;

  public GuestStayFacade(Database database, EventBus eventBus) {
    this.database = database;
    this.eventBus = eventBus;
  }

  public void checkInGuest(CheckInGuest command) {
    var account = GuestStayAccount.checkIn(command.guestStayAccountId(), command.now());

    database.store(GuestStayAccount.class, command.guestStayAccountId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public void recordCharge(RecordCharge command) {
    var account = database.get(GuestStayAccount.class, command.guestStayAccountId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordCharge(command.amount(), command.now());

    database.store(GuestStayAccount.class, command.guestStayAccountId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public void recordPayment(RecordPayment command) {
    var account = database.get(GuestStayAccount.class, command.guestStayAccountId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordPayment(command.amount(), command.now());

    database.store(GuestStayAccount.class, command.guestStayAccountId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public void checkOutGuest(CheckOutGuest command) {
    var account = database.get(GuestStayAccount.class, command.guestStayAccountId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.checkOut(command.groupCheckoutId(), command.now());

    database.store(GuestStayAccount.class, command.guestStayAccountId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    eventBus.publish(new Object[]{
      new GroupCheckoutInitiated(
        command.groupCheckoutId(),
        command.clerkId(),
        command.guestStayAccountIds(),
        command.now()
      )
    });
  }
}
