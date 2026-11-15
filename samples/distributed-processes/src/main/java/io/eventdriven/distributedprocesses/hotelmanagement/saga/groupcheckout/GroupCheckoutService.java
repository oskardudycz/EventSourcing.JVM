package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import io.eventdriven.distributedprocesses.core.entities.EntityStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import org.springframework.core.retry.RetryTemplate;

import java.util.Optional;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.toArray;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckoutCommand.*;

public class GroupCheckoutService {
  private final EntityStore<GroupCheckout, GroupCheckoutEvent> store;
  private RetryTemplate retryTemplate;

  public GroupCheckoutService(
    EntityStore<GroupCheckout, GroupCheckoutEvent> store,
    RetryTemplate retryTemplate
  ) {
    this.store = store;
    this.retryTemplate = retryTemplate;
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
    return retryTemplate.invoke(() -> handle(command.groupCheckoutId(), command));
  }

  private Optional<ETag> handle(UUID id, GroupCheckoutCommand command) {
    return retryTemplate.invoke(() ->
      store.getAndUpdate(
        (state) -> GroupCheckoutDecider.handle(command, state).orElse(toArray()),
        id
      )
    );
  }
}
