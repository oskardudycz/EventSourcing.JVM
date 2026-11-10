package io.eventdriven.eventdrivenarchitecture.e05_consistency.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.Outbox;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GuestStayAccountFacade {
  private final Database database;
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database database, MessageBus messageBus) {
    this.database = database;
    this.collection = database.collection(GuestStayAccount.class);
    this.messageBus = messageBus;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var account = GuestStayAccount.checkIn(command.guestStayId(), command.now());

    Outbox.storeAndPublish(database, messageBus, account.dequeueUncommittedEvents(),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), account));
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordCharge(command.amount(), command.now());

    Outbox.storeAndPublish(database, messageBus, account.dequeueUncommittedEvents(),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), account));
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordPayment(command.amount(), command.now());

    Outbox.storeAndPublish(database, messageBus, account.dequeueUncommittedEvents(),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), account));
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.checkout(command.now(), command.groupCheckOutId());

    Outbox.storeAndPublish(database, messageBus, account.dequeueUncommittedEvents(),
      transaction -> transaction.collection(GuestStayAccount.class).store(command.guestStayId(), account));
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
