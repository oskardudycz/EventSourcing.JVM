package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.Database;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.groupcheckouts.GroupCheckoutEvent;
import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccount;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class GuestStayFacade {
  private final Database.Collection<GuestStayAccount> collection;
  private final MessageBus messageBus;

  public GuestStayFacade(Database.Collection<GuestStayAccount> collection, MessageBus messageBus) {
    this.collection = collection;
    this.messageBus = messageBus;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var account = GuestStayAccount.checkIn(command.guestStayId(), command.now());

    collection.store(command.guestStayId(), account);
    messageBus.publish(account.dequeueUncommittedEvents());
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordCharge(command.amount(), command.now());

    collection.store(command.guestStayId(), account);
    messageBus.publish(account.dequeueUncommittedEvents());
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.recordPayment(command.amount(), command.now());

    collection.store(command.guestStayId(), account);
    messageBus.publish(account.dequeueUncommittedEvents());
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    var account = collection.get(command.guestStayId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    account.checkout(command.now(), command.groupCheckOutId());

    collection.store(command.guestStayId(), account);
    messageBus.publish(account.dequeueUncommittedEvents());
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
