package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.choreography.gueststayaccounts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;

public final class GuestStayAccountsConfig {
    public static void configureGuestStayAccounts(CommandBus commandBus, GuestStayAccountFacade guestStayFacade) {
        commandBus.handle(CheckOutGuest.class, guestStayFacade::checkOutGuest);
    }
}
