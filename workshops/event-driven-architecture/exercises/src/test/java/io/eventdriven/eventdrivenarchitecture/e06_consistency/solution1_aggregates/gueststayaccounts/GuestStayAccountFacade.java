package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.BackgroundJobs;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

public class GuestStayAccountFacade {
  private final Database database;
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database database, MessageBus messageBus, BackgroundJobs backgroundJobs) {
    this.database = database;
    this.collection = database.collection(GuestStayAccount.class);
    this.messageBus = messageBus;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var account = GuestStayAccount.checkIn(command.guestStayId(), command.now());

    collection.store(command.guestStayId(), account, 0);
    messageBus.publish(account.dequeueUncommittedEvents());
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    handle(command.guestStayId(), account -> account.recordCharge(command.amount(), command.now()));
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    handle(command.guestStayId(), account -> account.recordPayment(command.amount(), command.now()));
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    handle(command.guestStayId(), account -> account.checkout(command.now(), command.groupCheckOutId()));
  }

  private void handle(UUID guestStayId, Consumer<GuestStayAccount> handle) {
    var events = Retry.onConflict(() -> {
      var loaded = collection.get(guestStayId)
        .orElseThrow(() -> new IllegalStateException("Entity not found"));
      var account = loaded.document();

      handle.accept(account);

      collection.store(guestStayId, account, loaded.version());

      return account.dequeueUncommittedEvents();
    });

    messageBus.publish(events);
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
