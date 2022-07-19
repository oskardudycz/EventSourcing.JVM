package io.eventdriven.distributedprocesses.hotelmanagement.gueststayaccount;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.gueststayaccount.GuestStayAccountEvent.*;

public class GuestStayAccount extends AbstractAggregate<GuestStayAccountEvent, UUID> {
  private enum Status {
    Open,
    Checkout
  }

  private Status status;
  private double balance;

  public static GuestStayAccount open(UUID guestStayAccountId, OffsetDateTime openedAt) {
    return new GuestStayAccount(
      guestStayAccountId,
      openedAt
    );
  }

  private GuestStayAccount(
    UUID guestStayAccountId,
    OffsetDateTime openedAt
  ) {
    enqueue(new GuestStayAccountOpened(guestStayAccountId, openedAt));
  }

  public void recordCharge(double amount, OffsetDateTime now) {
    if (status != Status.Open)
      throw new RuntimeException("Cannot record charge if status is other than Open");

    enqueue(new ChargeRecorded(id(), amount, now));
  }

  public void recordPayment(double amount, OffsetDateTime now) {
    if (status != Status.Open)
      throw new RuntimeException("Cannot record payment if status is other than Open");

    enqueue(new PaymentRecorded(id(), amount, now));
  }

  public void checkout(@Nullable UUID groupCheckoutId, OffsetDateTime now) {
    if (status != Status.Open || balance != 0) {
      enqueue(new GuestAccountCheckoutFailed(id(), groupCheckoutId, OffsetDateTime.now()));
      return;
    }
    enqueue(new GuestAccountCheckoutCompleted(id(), groupCheckoutId, now));
  }

  @Override
  public void when(GuestStayAccountEvent event) {
    switch (event) {
      case GuestStayAccountOpened opened -> {
        id = opened.guestStayAccountId();
        balance = 0;
        status = Status.Open;
      }
      case ChargeRecorded chargeRecorded ->
        balance -= chargeRecorded.amount();
      case PaymentRecorded paymentRecorded ->
        balance += paymentRecorded.amount();
      case GuestAccountCheckoutCompleted checkoutCompleted ->
        status = Status.Checkout;
      case GuestAccountCheckoutFailed ignored -> {
      }
    }
  }
}
