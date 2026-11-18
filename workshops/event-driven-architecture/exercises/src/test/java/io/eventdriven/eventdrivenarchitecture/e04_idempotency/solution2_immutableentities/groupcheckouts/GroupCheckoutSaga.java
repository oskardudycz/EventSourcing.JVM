package io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;

import io.eventdriven.eventdrivenarchitecture.e04_idempotency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent;
import java.util.Arrays;
import java.util.List;

public final class GroupCheckoutSaga {
    private final MessageBus messageBus;

    public GroupCheckoutSaga(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    public void handle(GroupCheckoutEvent.GroupCheckoutInitiated event) {
        messageBus.publish(Arrays.stream(event.guestStayAccountIds())
                .map(guestAccountId -> new CheckOutGuest(guestAccountId, event.initiatedAt(), event.groupCheckoutId()))
                .toList());
    }

    public void handle(GuestStayAccountEvent.GuestCheckedOut event) {
        if (event.groupCheckoutId() == null) return;

        messageBus.publish(List.of(new RecordGuestCheckoutCompletion(
                event.groupCheckoutId(), event.guestStayAccountId(), event.completedAt())));
    }

    public void handle(GuestStayAccountEvent.GuestCheckoutFailed event) {
        if (event.groupCheckoutId() == null) return;

        messageBus.publish(List.of(
                new RecordGuestCheckoutFailure(event.groupCheckoutId(), event.guestStayAccountId(), event.failedAt())));
    }
}
