package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public record GuestStayAccount(
  UUID id,
  double balance,
  Status status,
  List<UUID> recordedChargeIds,
  List<UUID> recordedPaymentIds,
  @Nullable
  UUID groupCheckoutId
) {
  public enum Status {
    OPEN,
    CHECKED_OUT
  }

  public boolean isSettled() {
    return balance == 0;
  }

  public static GuestStayAccount evolve(GuestStayAccount state, GuestStayAccountEvent event) {
    return switch (event) {
      case GuestCheckedIn opened -> new GuestStayAccount(
        opened.guestStayAccountId(),
        0,
        Status.OPEN,
        List.of(),
        List.of(),
        null
      );
      case ChargeRecorded chargeRecorded -> new GuestStayAccount(
        state.id(),
        state.balance() - chargeRecorded.amount(),
        state.status(),
        Stream.concat(state.recordedChargeIds().stream(), Stream.of(chargeRecorded.chargeId())).toList(),
        state.recordedPaymentIds(),
        state.groupCheckoutId()
      );
      case PaymentRecorded paymentRecorded -> new GuestStayAccount(
        state.id(),
        state.balance() + paymentRecorded.amount(),
        state.status(),
        state.recordedChargeIds(),
        Stream.concat(state.recordedPaymentIds().stream(), Stream.of(paymentRecorded.paymentId())).toList(),
        state.groupCheckoutId()
      );
      case GuestCheckedOut checkedOut -> new GuestStayAccount(
        state.id(),
        state.balance(),
        Status.CHECKED_OUT,
        state.recordedChargeIds(),
        state.recordedPaymentIds(),
        checkedOut.groupCheckoutId()
      );
      case GuestCheckoutFailed ignored -> state;
    };
  }

  public static final GuestStayAccount INITIAL = new GuestStayAccount(
    null,
    Double.MIN_VALUE,
    null,
    List.of(),
    List.of(),
    null
  );
}
