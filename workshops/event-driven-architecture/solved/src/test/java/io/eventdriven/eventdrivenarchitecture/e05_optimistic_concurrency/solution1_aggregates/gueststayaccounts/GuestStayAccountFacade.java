package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class GuestStayAccountFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayAccountFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
    this.collection = collection;
    this.messageBus = messageBus;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var events = Retry.onConflict(() -> {
      if (collection.get(command.guestStayId()).isPresent())
        return List.of();

      var account = GuestStayAccount.checkIn(command.guestStayId(), command.now());

      collection.store(command.guestStayId(), account, 0);

      return account.dequeueUncommittedEvents();
    });

    messageBus.publish(events);
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    handle(command.guestStayId(), account -> account.recordCharge(command.chargeId(), command.amount(), command.now()));
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    handle(command.guestStayId(), account -> account.recordPayment(command.paymentId(), command.amount(), command.now()));
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
      UUID chargeId,
      double amount,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record RecordPayment(
      UUID guestStayId,
      UUID paymentId,
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
