package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.distributedprocesses.core.entities.EntityStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.core.retries.RetryPolicy;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;

import java.util.UUID;

public class GuestStayAccountService {
  private final EntityStore<GuestStayAccount, GuestStayAccountEvent> store;
  private RetryPolicy retryPolicy;

  public GuestStayAccountService(
    EntityStore<GuestStayAccount, GuestStayAccountEvent> store,
    RetryPolicy retryPolicy
  ) {
    this.store = store;
    this.retryPolicy = retryPolicy;
  }

  public ETag handle(CheckInGuest command) {
    return handle(command.guestStayAccountId(), command);
  }

  public ETag handle(RecordCharge command) {
    return handle(command.guestStayAccountId(), command);
  }

  public ETag handle(RecordPayment command) {
    return handle(command.guestStayAccountId(), command);
  }

  public ETag handle(CheckOutGuest command) {
    return retryPolicy.run(ack -> {
      var result = handle(command.guestStayAccountId(), command);
      ack.accept(result);
    });
  }

  private ETag handle(UUID id, GuestStayAccountCommand command) {
    return store.getAndUpdate(
      (state) -> GuestStayAccountDecider.handle(command, state),
      id
    );
  }
}
