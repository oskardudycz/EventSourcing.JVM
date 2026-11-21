package io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.GuestStayAccountStatus;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckInGuest;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordCharge;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordPayment;
import io.eventdriven.eventdrivenarchitecture.e04_idempotency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GuestStayAccountDecider {
    public static List<GuestStayAccountEvent> handle(CheckInGuest command) {
        return List.of(new GuestCheckedIn(command.guestStayAccountId(), command.now()));
    }

    public static List<GuestStayAccountEvent> handle(RecordCharge command, GuestStayAccount state) {
        if (state.status() != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record charge if status is other than Open");

        if (state.recordedChargeIds().contains(command.chargeId())) return List.of();

        return List.of(new ChargeRecorded(state.id(), command.chargeId(), command.amount(), command.now()));
    }

    public static List<GuestStayAccountEvent> handle(RecordPayment command, GuestStayAccount state) {
        if (state.status() != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record payment if status is other than Open");

        if (state.recordedPaymentIds().contains(command.paymentId())) return List.of();

        return List.of(new PaymentRecorded(state.id(), command.paymentId(), command.amount(), command.now()));
    }

    public static List<GuestStayAccountEvent> handle(CheckOutGuest command, GuestStayAccount state) {
        if (state.status() == GuestStayAccountStatus.CHECKED_OUT
                && Objects.equals(state.groupCheckoutId(), command.groupCheckoutId())) return List.of();

        if (state.status() != GuestStayAccountStatus.OPEN || !state.isSettled()) {
            return List.of(new GuestCheckoutFailed(
                    state.id(),
                    !state.isSettled()
                            ? GuestCheckoutFailed.FailureReason.BALANCE_NOT_SETTLED
                            : GuestCheckoutFailed.FailureReason.INVALID_STATE,
                    command.now(),
                    command.groupCheckoutId()));
        }
        return List.of(new GuestCheckedOut(state.id(), command.now(), command.groupCheckoutId()));
    }

    public static List<GuestStayAccountEvent> decide(GuestStayAccountCommand command, GuestStayAccount state) {
        return switch (command) {
            case CheckInGuest checkInGuest -> handle(checkInGuest);
            case RecordCharge recordCharge -> handle(recordCharge, state);
            case RecordPayment recordPayment -> handle(recordPayment, state);
            case CheckOutGuest checkOutGuest -> handle(checkOutGuest, state);
        };
    }

    public sealed interface GuestStayAccountCommand {
        record CheckInGuest(UUID guestStayAccountId, OffsetDateTime now) implements GuestStayAccountCommand {}

        record RecordCharge(UUID guestStayAccountId, UUID chargeId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record RecordPayment(UUID guestStayAccountId, UUID paymentId, double amount, OffsetDateTime now)
                implements GuestStayAccountCommand {}

        record CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now, UUID groupCheckoutId)
                implements GuestStayAccountCommand {
            public CheckOutGuest(UUID guestStayAccountId, OffsetDateTime now) {
                this(guestStayAccountId, now, null);
            }
        }
    }
}
