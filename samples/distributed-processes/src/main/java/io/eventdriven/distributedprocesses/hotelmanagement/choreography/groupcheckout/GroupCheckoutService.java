package io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.toArray;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckoutCommand.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckoutEvent.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountCommand.*;

import io.eventdriven.distributedprocesses.core.commands.CommandBus;
import io.eventdriven.distributedprocesses.core.entities.EntityStore;
import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.retry.RetryTemplate;

public class GroupCheckoutService {
    private final EntityStore<GroupCheckout, GroupCheckoutEvent> store;
    private final CommandBus commandBus;
    private RetryTemplate retryTemplate;

    public GroupCheckoutService(
            EntityStore<GroupCheckout, GroupCheckoutEvent> store, CommandBus commandBus, RetryTemplate retryTemplate) {
        this.store = store;
        this.commandBus = commandBus;
        this.retryTemplate = retryTemplate;
    }

    public Optional<ETag> handle(InitiateGroupCheckout command) {
        return handle(command.groupCheckoutId(), command);
    }

    public void on(GroupCheckoutInitiated groupCheckoutInitiated) {
        for (var guestAccountId : groupCheckoutInitiated.guestStayAccountIds()) {
            commandBus.schedule(new CheckOutGuest(
                    guestAccountId, groupCheckoutInitiated.groupCheckoutId(), groupCheckoutInitiated.initiatedAt()));
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
        return retryTemplate.invoke(() -> store.getAndUpdate(
                (state) -> GroupCheckoutDecider.handle(command, state).orElse(toArray()), id));
    }
}
