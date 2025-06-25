package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution1_aggregates.gueststayaccounts;

import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.EventStore;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GuestStayAccountFacade {
  private final EventStore eventStore;

  public GuestStayAccountFacade(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    var account = GuestStayAccount.checkIn(command.guestStayId(), command.now());

    eventStore.appendToStream(command.guestStayId().toString(), account.dequeueUncommittedEvents());
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
    var account = getAccount(command.guestStayId());

    account.recordCharge(command.amount(), command.now());

    eventStore.appendToStream(command.guestStayId().toString(), account.dequeueUncommittedEvents());
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
    var account = getAccount(command.guestStayId());

    account.recordPayment(command.amount(), command.now());


    eventStore.appendToStream(command.guestStayId().toString(), account.dequeueUncommittedEvents());
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
    var account = getAccount(command.guestStayId());

    account.checkout(command.now(), command.groupCheckOutId());

    eventStore.appendToStream(command.guestStayId().toString(), account.dequeueUncommittedEvents());
  }

  private GuestStayAccount getAccount(UUID accountId) {
    return eventStore.aggregateStream(
      accountId.toString(),
      (GuestStayAccount state, GuestStayAccountEvent event) -> {
        state.apply(event);
        return state;
      },
      GuestStayAccount::initial
    );
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
