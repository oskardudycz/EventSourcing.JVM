package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.toArray;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckoutCommand.*;

import io.eventdriven.distributedprocesses.core.entities.EntityStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.core.retries.RetryPolicy;
import java.util.Optional;
import java.util.UUID;

public class GroupCheckoutService {
  private final EntityStore<GroupCheckout, GroupCheckoutEvent> store;
  private RetryPolicy retryPolicy;

  public GroupCheckoutService(
      EntityStore<GroupCheckout, GroupCheckoutEvent> store, RetryPolicy retryPolicy) {
    this.store = store;
    this.retryPolicy = retryPolicy;
  }

  public Optional<ETag> handle(InitiateGroupCheckout command) {
    return handle(command.groupCheckoutId(), command);
  }

  public Optional<ETag> handle(RecordGuestCheckoutsInitiation command) {
    return handle(command.groupCheckoutId(), command);
  }

  public Optional<ETag> handle(RecordGuestCheckoutCompletion command) {
    return handle(command.groupCheckoutId(), command);
  }

  public Optional<ETag> handle(RecordGuestCheckoutFailure command) {
    return retryPolicy.run(ack -> {
      var result = handle(command.groupCheckoutId(), command);
      ack.accept(result);
    });
  }

  private Optional<ETag> handle(UUID id, GroupCheckoutCommand command) {
    return retryPolicy.run(ack -> {
      var result = store.getAndUpdate(
          (state) -> GroupCheckoutDecider.handle(command, state).orElse(toArray()), id);
      ack.accept(result);
    });
  }
}
