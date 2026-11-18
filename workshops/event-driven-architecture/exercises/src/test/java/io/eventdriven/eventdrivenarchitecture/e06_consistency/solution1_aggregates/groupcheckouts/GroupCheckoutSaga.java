package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e06_consistency.core.SagaResult.*;
import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.SagaResult;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import java.util.Arrays;
import java.util.List;

public final class GroupCheckoutSaga {
    public static List<Command<CheckOutGuest>> handle(GroupCheckoutEvent.GroupCheckoutInitiated event) {
        return Arrays.stream(event.guestStayAccountIds())
                .map(guestAccountId ->
                        Send(new CheckOutGuest(guestAccountId, event.initiatedAt(), event.groupCheckoutId())))
                .toList();
    }

    public static SagaResult handle(GuestStayAccountEvent.GuestCheckedOut event) {
        if (event.groupCheckoutId() == null) return Ignore;

        return Send(new RecordGuestCheckoutCompletion(
                event.groupCheckoutId(), event.guestStayAccountId(), event.completedAt()));
    }

    public static SagaResult handle(GuestStayAccountEvent.GuestCheckoutFailed event) {
        if (event.groupCheckoutId() == null) return Ignore;

        return Send(
                new RecordGuestCheckoutFailure(event.groupCheckoutId(), event.guestStayAccountId(), event.failedAt()));
    }
}
