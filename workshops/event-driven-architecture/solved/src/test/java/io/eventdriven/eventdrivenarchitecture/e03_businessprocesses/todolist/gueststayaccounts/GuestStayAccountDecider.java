package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccount.GuestStayAccountStatus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckInGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordCharge;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordPayment;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class GuestStayAccountDecider {
    public static GuestCheckedIn handle(CheckInGuest command) {
        return new GuestCheckedIn(command.guestStayAccountId(), command.now());
    }

    public static ChargeRecorded handle(RecordCharge command, GuestStayAccount state) {
        if (state.status() != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record charge if status is other than Open");

        return new ChargeRecorded(state.id(), command.amount(), command.now());
    }

    public static PaymentRecorded handle(RecordPayment command, GuestStayAccount state) {
        if (state.status() != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record payment if status is other than Open");

        return new PaymentRecorded(state.id(), command.amount(), command.now());
    }

    public static GuestStayAccountEvent handle(CheckOutGuest command, GuestStayAccount state) {
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
            case CheckInGuest checkInGuest -> handle(checkInGuest);
            case RecordCharge recordCharge -> handle(recordCharge, state);
            case RecordPayment recordPayment -> handle(recordPayment, state);
            case CheckOutGuest checkOutGuest -> handle(checkOutGuest, state);
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
