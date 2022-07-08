package io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.http.ETag;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout.GroupCheckoutCommand.*;

public class GroupCheckoutService {
  private final AggregateStore<GroupCheckout, GroupCheckoutEvent, UUID> store;

  public GroupCheckoutService(AggregateStore<GroupCheckout, GroupCheckoutEvent, UUID> store) {
    this.store = store;
  }

  public ETag handle(InitiateGroupCheckout command) {
    return store.add(
      GroupCheckout.initiate(
        command.groupCheckoutId(),
        command.clerkId(),
        command.guestStayAccountIds(),
        OffsetDateTime.now()
      )
    );
  }

  public ETag handle(RecordGuestStayInitiation command) {
    return store.getAndUpdate(
      current -> current.recordGuestStaysCheckoutInitiation(
        command.guestStayAccountIds(),
        OffsetDateTime.now()
      ),
      command.groupCheckoutId()
    );
  }

  public ETag handle(RecordGuestCheckoutCompletion command) {
    return store.getAndUpdate(
      current -> current.recordGuestStayCheckoutCompletion(
        command.guestStayAccountId(),
        OffsetDateTime.now()
      ),
      command.groupCheckoutId()
    );
  }

  public ETag handle(RecordGuestCheckoutFailure command) {
    return store.getAndUpdate(
      current -> current.recordGuestStayCheckoutFailure(
        command.guestStayAccountId(),
        OffsetDateTime.now()
      ),
      command.groupCheckoutId()
    );
  }
}
