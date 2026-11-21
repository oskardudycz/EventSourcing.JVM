package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts;

import static io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent.*;

import io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.core.AbstractAggregate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class GuestStayAccount extends AbstractAggregate<GuestStayAccountEvent, UUID> {
    private enum GuestStayAccountStatus {
        OPEN,
        CHECKED_OUT
    }

    private GuestStayAccountStatus status;
    private double balance;

    public static GuestStayAccount checkIn(UUID guestStayAccountId, OffsetDateTime openedAt) {
        return new GuestStayAccount(guestStayAccountId, openedAt);
    }

    // Default constructor for Jackson
    private GuestStayAccount() {}

    private GuestStayAccount(UUID guestStayAccountId, OffsetDateTime openedAt) {
        enqueue(new GuestCheckedIn(guestStayAccountId, openedAt));
    }

    public void recordCharge(double amount, OffsetDateTime now) {
        if (status != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record charge if status is other than Open");

        enqueue(new ChargeRecorded(id(), amount, now));
    }

    public void recordPayment(double amount, OffsetDateTime now) {
        if (status != GuestStayAccountStatus.OPEN)
            throw new RuntimeException("Cannot record payment if status is other than Open");

        enqueue(new PaymentRecorded(id(), amount, now));
    }

    public void checkout(OffsetDateTime now, @Nullable UUID groupCheckoutId) {
        if (status != GuestStayAccountStatus.OPEN || balance != 0) {
            enqueue(new GuestCheckoutFailed(
                    id(),
                    balance != 0
                            ? GuestCheckoutFailed.FailureReason.BALANCE_NOT_SETTLED
                            : GuestCheckoutFailed.FailureReason.INVALID_STATE,
                    now,
                    groupCheckoutId));
            return;
        }
        enqueue(new GuestCheckedOut(id(), now, groupCheckoutId));
    }

    @Override
    public void apply(GuestStayAccountEvent event) {
        switch (event) {
            case GuestCheckedIn opened -> {
                id = opened.guestStayAccountId();
                balance = 0;
                status = GuestStayAccountStatus.OPEN;
            }
            case ChargeRecorded chargeRecorded -> balance -= chargeRecorded.amount();
            case PaymentRecorded paymentRecorded -> balance += paymentRecorded.amount();
            case GuestCheckedOut ignored -> status = GuestStayAccountStatus.CHECKED_OUT;
            case GuestCheckoutFailed ignored -> {}
        }
    }
}
