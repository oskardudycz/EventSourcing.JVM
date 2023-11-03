package io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount;

import io.eventdriven.distributedprocesses.core.entities.EntityStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.core.retries.RetryPolicy;
import io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountCommand.CheckInGuest;
import io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountCommand.RecordCharge;
import io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountCommand.RecordPayment;
import java.util.Optional;
import java.util.UUID;

public class GuestStayAccountService {
  private final EntityStore<GuestStayAccount, GuestStayAccountEvent> store;
  private RetryPolicy retryPolicy;

  public GuestStayAccountService(
      EntityStore<GuestStayAccount, GuestStayAccountEvent> store, RetryPolicy retryPolicy) {
    this.store = store;
    this.retryPolicy = retryPolicy;
  }

  public ETag handle(CheckInGuest command) {
    return handle(command.guestStayAccountId(), command)
        .orElseThrow(() -> new IllegalStateException("Invalid state"));
  }

  public ETag handle(RecordCharge command) {
    return handle(command.guestStayAccountId(), command)
        .orElseThrow(() -> new IllegalStateException("Invalid state"));
  }

  public ETag handle(RecordPayment command) {
    return handle(command.guestStayAccountId(), command)
        .orElseThrow(() -> new IllegalStateException("Invalid state"));
  }

  public Optional<ETag> handle(CheckOutGuest command) {
    return retryPolicy.run(
        ack -> {
          var result = handle(command.guestStayAccountId(), command);
          ack.accept(result);
        });
  }

  private Optional<ETag> handle(UUID id, GuestStayAccountCommand command) {
    return store.getAndUpdate((state) -> GuestStayAccountDecider.handle(command, state), id);
  }
}
