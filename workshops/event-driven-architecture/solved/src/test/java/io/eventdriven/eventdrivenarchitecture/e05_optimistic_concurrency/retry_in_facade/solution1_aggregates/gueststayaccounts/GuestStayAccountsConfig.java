package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_facade.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.MessageBus;

public final class GuestStayAccountsConfig {
    public static void configureGuestStayAccounts(MessageBus messageBus, GuestStayAccountFacade guestStayFacade) {
        messageBus.subscribe(
                GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest.class, guestStayFacade::checkOutGuest);
    }
}
