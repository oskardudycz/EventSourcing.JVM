package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution1_aggregates.groupcheckouts;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;

public final class GroupCheckoutsConfig {
    public static void configureGroupCheckouts(MessageBus messageBus, GroupCheckoutFacade groupCheckoutFacade) {
        var saga = new GroupCheckoutSaga(messageBus);

        messageBus
                .subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, saga::handle)
                .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, saga::handle)
                .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, saga::handle);

        messageBus
                .subscribe(RecordGuestCheckoutCompletion.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
                .subscribe(RecordGuestCheckoutFailure.class, groupCheckoutFacade::recordGuestCheckoutFailure);
    }
}
