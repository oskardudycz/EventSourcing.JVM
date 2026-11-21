package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.GuestStayAccountStatus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class GuestStayAccountDecider {
    public static GuestCheckedIn handle(GuestStayAccountCommand.CheckInGuest command) {
        return new GuestCheckedIn(command.guestStayAccountId(), command.now());
    }

    public static ChargeRecorded handle(GuestStayAccountCommand.RecordCharge command, GuestStayAccount state) {
        if (state.status() != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record charge if status is other than Open");

        return new ChargeRecorded(state.id(), command.amount(), command.now());
    }

    public static PaymentRecorded handle(GuestStayAccountCommand.RecordPayment command, GuestStayAccount state) {
        if (state.status() != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record payment if status is other than Open");

        return new PaymentRecorded(state.id(), command.amount(), command.now());
    }

    public static GuestStayAccountEvent handle(GuestStayAccountCommand.CheckOutGuest command, GuestStayAccount state) {
        if (state.status() != GuestStayAccountStatus.OPEN || !state.isSettled()) {
            return new GuestCheckoutFailed(
                    state.id(),
                    !state.isSettled()
                            ? GuestCheckoutFailed.FailureReason.BALANCE_NOT_SETTLED
                            : GuestCheckoutFailed.FailureReason.INVALID_STATE,
                    command.now(),
                    command.groupCheckoutId());
        }
        return new GuestCheckedOut(state.id(), command.now(), command.groupCheckoutId());
    }

    public static GuestStayAccountEvent decide(GuestStayAccountCommand command, GuestStayAccount state) {
        return switch (command) {
            case GuestStayAccountCommand.CheckInGuest checkInGuest -> handle(checkInGuest);
            case GuestStayAccountCommand.RecordCharge recordCharge -> handle(recordCharge, state);
            case GuestStayAccountCommand.RecordPayment recordPayment -> handle(recordPayment, state);
            case GuestStayAccountCommand.CheckOutGuest checkOutGuest -> handle(checkOutGuest, state);
        };
    }

    public sealed interface GuestStayAccountCommand {
        record CheckInGuest(UUID guestStayAccountId, OffsetDateTime now) implements GuestStayAccountCommand {}

        record RecordCharge(UUID guestStayAccountId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record RecordPayment(UUID guestStayAccountId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now, UUID groupCheckoutId)
                implements GuestStayAccountCommand {
            public CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now) {
                this(guestStayAccountId, now, null);
            }
        }
    }
}
