package io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;
import java.util.UUID;

public record GuestStayAccount(UUID id, double balance, GuestStayAccountStatus status) {
    public enum GuestStayAccountStatus {
        NOT_EXISTING,
        OPEN,
        CHECKED_OUT
    }

    public boolean isSettled() {
        return balance == 0;
    }

    public static GuestStayAccount evolve(GuestStayAccount state, GuestStayAccountEvent event) {
        return switch (event) {
            case GuestCheckedIn opened ->
                new GuestStayAccount(opened.guestStayAccountId(), 0, GuestStayAccountStatus.OPEN);
            case ChargeRecorded chargeRecorded ->
                new GuestStayAccount(state.id(), state.balance() - chargeRecorded.amount(), state.status());
            case PaymentRecorded paymentRecorded ->
                new GuestStayAccount(state.id(), state.balance() + paymentRecorded.amount(), state.status());
            case GuestCheckedOut ignored ->
                new GuestStayAccount(state.id(), state.balance(), GuestStayAccountStatus.CHECKED_OUT);
            case GuestCheckoutFailed ignored -> state;
        };
    }

    public static final GuestStayAccount INITIAL =
            new GuestStayAccount(null, Double.MIN_VALUE, GuestStayAccountStatus.NOT_EXISTING);
}
