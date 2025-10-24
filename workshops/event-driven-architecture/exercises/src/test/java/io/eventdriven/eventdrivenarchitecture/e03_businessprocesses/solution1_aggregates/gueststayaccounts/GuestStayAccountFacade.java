package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GuestStayAccountFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final EventBus eventBus;

  public GuestStayAccountFacade(Database.Collection<GuestStayAccount> database, EventBus eventBus) {
    this.collection = database;
    this.eventBus = eventBus;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var account = GuestStayAccount.checkIn(command.guestStayId(), command.now());

    collection.store(command.guestStayId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordCharge(command.amount(), command.now());

    collection.store(command.guestStayId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordPayment(command.amount(), command.now());

    collection.store(command.guestStayId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.checkout(command.now(), command.groupCheckOutId());

    collection.store(command.guestStayId(), account);
    eventBus.publish(account.dequeueUncommittedEvents());
  }

  public sealed interface GuestStayAccountCommand {
    record CheckInGuest(
      UUID guestStayId,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record RecordCharge(
      UUID guestStayId,
      double amount,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record RecordPayment(
      UUID guestStayId,
      double amount,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record CheckOutGuest(
      UUID guestStayId,
      OffsetDateTime now,
      UUID groupCheckOutId
    ) implements GuestStayAccountCommand {
      public CheckOutGuest(UUID guestStayId, OffsetDateTime now) {
        this(guestStayId, now, null);
      }
    }
  }
}
