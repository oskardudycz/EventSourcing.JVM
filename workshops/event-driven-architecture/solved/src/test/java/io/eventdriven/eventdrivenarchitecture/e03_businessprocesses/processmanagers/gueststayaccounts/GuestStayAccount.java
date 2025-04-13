package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.AbstractAggregate;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent.*;

public class GuestStayAccount  extends AbstractAggregate<GuestStayAccountEvent, UUID> {
  private enum Status {
    OPEN,
    CHECKED_OUT
  }

  private Status status;
  private double balance;

  public static GuestStayAccount checkIn(UUID guestStayAccountId, OffsetDateTime openedAt) {
    return new GuestStayAccount(
      guestStayAccountId,
      openedAt
    );
  }

  // Default constructor for Jackson
  private GuestStayAccount() {
  }

  private GuestStayAccount(
    UUID guestStayAccountId,
    OffsetDateTime openedAt
  ) {
    enqueue(new GuestCheckedIn(guestStayAccountId, openedAt));
  }

  public void recordCharge(double amount, OffsetDateTime now) {
    if (status != Status.OPEN)
      throw new RuntimeException("Cannot record charge if status is other than Open");

    enqueue(new ChargeRecorded(id(), amount, now));
  }

  public void recordPayment(double amount, OffsetDateTime now) {
    if (status != Status.OPEN)
      throw new RuntimeException("Cannot record payment if status is other than Open");

    enqueue(new PaymentRecorded(id(), amount, now));
  }

  public void checkout(OffsetDateTime now, @Nullable UUID groupCheckoutId) {
    if (status != Status.OPEN || balance != 0) {
      enqueue(
        new GuestCheckoutFailed(id(),
          balance != 0 ? GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED : GuestCheckoutFailed.Reason.INVALID_STATE,
          now,
          groupCheckoutId
        )
      );
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
        status = Status.OPEN;
      }
      case ChargeRecorded chargeRecorded -> balance -= chargeRecorded.amount();
      case PaymentRecorded paymentRecorded ->
        balance += paymentRecorded.amount();
      case GuestCheckedOut ignored -> status = Status.CHECKED_OUT;
      case GuestCheckoutFailed ignored -> {
      }
    }
  }
}
