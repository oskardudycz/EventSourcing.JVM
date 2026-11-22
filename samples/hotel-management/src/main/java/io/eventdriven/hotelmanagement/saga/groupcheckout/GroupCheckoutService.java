package io.eventdriven.hotelmanagement.saga.groupcheckout;

import static io.eventdriven.core.collections.CollectionsExtensions.toArray;
import static io.eventdriven.hotelmanagement.saga.groupcheckout.GroupCheckoutCommand.*;

import io.eventdriven.core.entities.EntityStore;
import io.eventdriven.core.http.ETag;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.retry.RetryTemplate;

public class GroupCheckoutService {
    private final EntityStore<GroupCheckout, GroupCheckoutEvent> store;
    private RetryTemplate retryTemplate;

    public GroupCheckoutService(EntityStore<GroupCheckout, GroupCheckoutEvent> store, RetryTemplate retryTemplate) {
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
        return retryTemplate.invoke(() -> store.getAndUpdate(
                (state) -> GroupCheckoutDecider.handle(command, state).orElse(toArray()), id));
    }
}
