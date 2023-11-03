package io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.toArray;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckoutCommand.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckoutEvent.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountCommand.*;

import io.eventdriven.distributedprocesses.core.commands.CommandBus;
import io.eventdriven.distributedprocesses.core.entities.EntityStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.core.retries.RetryPolicy;
import io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountEvent;
import java.util.Optional;
import java.util.UUID;

public class GroupCheckoutService {
  private final EntityStore<GroupCheckout, GroupCheckoutEvent> store;
  private final CommandBus commandBus;
  private RetryPolicy retryPolicy;

  public GroupCheckoutService(
      EntityStore<GroupCheckout, GroupCheckoutEvent> store,
      CommandBus commandBus,
      RetryPolicy retryPolicy) {
    this.store = store;
    this.commandBus = commandBus;
    this.retryPolicy = retryPolicy;
  }

  public Optional<ETag> handle(InitiateGroupCheckout command) {
    return handle(command.groupCheckoutId(), command);
  }

  public void on(GroupCheckoutInitiated groupCheckoutInitiated) {
    for (var guestAccountId : groupCheckoutInitiated.guestStayAccountIds()) {
      commandBus.schedule(
          new CheckOutGuest(
              guestAccountId,
              groupCheckoutInitiated.groupCheckoutId(),
              groupCheckoutInitiated.initiatedAt()));
    }

    handle(
        groupCheckoutInitiated.groupCheckoutId(),
        new RecordGuestCheckoutsInitiation(
            groupCheckoutInitiated.groupCheckoutId(),
            groupCheckoutInitiated.guestStayAccountIds(),
            groupCheckoutInitiated.initiatedAt()));
  }

  public void on(GuestStayAccountEvent.GuestCheckedOut guestCheckoutCompleted) {
    if (guestCheckoutCompleted.groupCheckoutId() == null) return;

    handle(
        guestCheckoutCompleted.groupCheckoutId(),
        new RecordGuestCheckoutCompletion(
            guestCheckoutCompleted.groupCheckoutId(),
            guestCheckoutCompleted.guestStayAccountId(),
            guestCheckoutCompleted.completedAt()));
  }

  public void on(GuestStayAccountEvent.GuestCheckoutFailed guestCheckoutFailed) {
    if (guestCheckoutFailed.groupCheckoutId() == null) return;

    handle(
        guestCheckoutFailed.groupCheckoutId(),
        new RecordGuestCheckoutFailure(
            guestCheckoutFailed.groupCheckoutId(),
            guestCheckoutFailed.guestStayAccountId(),
            guestCheckoutFailed.failedAt()));
  }

  private Optional<ETag> handle(UUID id, GroupCheckoutCommand command) {
    return retryPolicy.run(
        ack -> {
          var result =
              store.getAndUpdate(
                  (state) -> GroupCheckoutDecider.handle(command, state).orElse(toArray()), id);
          ack.accept(result);
        });
  }
}
