package io.eventdriven.introductiontoeventsourcing.e13_entities_definition;

import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.Database;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.EventStore;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GuestStayFacade {
  private final EventStore eventStore;

  public GuestStayFacade(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public void checkInGuest(GuestStayAccountCommand.CheckInGuest command) {
    // TODO: Fill the implementation calling your entity/aggregate
    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
  }

  public void recordCharge(GuestStayAccountCommand.RecordCharge command) {
//    var account = eventStore.aggregateStream(command.guestStayId(), ...)
//      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    // TODO: Fill the implementation calling your entity/aggregate
    // account.doSomething;
    Object[] events = new Object[]{};

    eventStore.appendToStream(command.guestStayId().toString(), events);

    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
  }

  public void recordPayment(GuestStayAccountCommand.RecordPayment command) {
//    var account = eventStore.aggregateStream(command.guestStayId(), ...)
//      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    // TODO: Fill the implementation calling your entity/aggregate
    // account.doSomething;
    Object[] events = new Object[]{};

    eventStore.appendToStream(command.guestStayId().toString(), events);

    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
  }

  public void checkOutGuest(GuestStayAccountCommand.CheckOutGuest command) {
//    var account = eventStore.aggregateStream(command.guestStayId(), ...)
//      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    // TODO: Fill the implementation calling your entity/aggregate
    // account.doSomething;
    Object[] events = new Object[]{};

    eventStore.appendToStream(command.guestStayId().toString(), events);

    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    // TODO: Fill the implementation publishing event
    throw new RuntimeException("TODO: Fill the implementation publishing event");
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
