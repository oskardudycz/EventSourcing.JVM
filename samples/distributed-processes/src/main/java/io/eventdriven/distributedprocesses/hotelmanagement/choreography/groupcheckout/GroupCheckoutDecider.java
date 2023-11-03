package io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.toArray;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckout.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckoutCommand.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckoutEvent.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GroupCheckoutDecider {
  public static Optional<GroupCheckoutEvent[]> handle(
      GroupCheckoutCommand command, GroupCheckout state) {
    return switch (command) {
      case InitiateGroupCheckout initiate:
        {
          if (!(state instanceof Initial initial)) yield Optional.empty();

          yield Optional.of(toArray(handle(initiate, initial)));
        }
      case RecordGuestCheckoutsInitiation recordGuestStayInitiation:
        {
          if (!(state instanceof InProgress inProgress)) yield Optional.empty();

          yield Optional.of(toArray(handle(recordGuestStayInitiation, inProgress)));
        }
      case RecordGuestCheckoutCompletion recordGuestCheckoutCompletion:
        {
          if (!(state instanceof InProgress inProgress)) yield Optional.empty();

          yield Optional.of(handle(recordGuestCheckoutCompletion, inProgress));
        }
      case RecordGuestCheckoutFailure checkOut:
        {
          if (!(state instanceof InProgress inProgress)) yield Optional.empty();

          yield Optional.of(handle(checkOut, inProgress));
        }
    };
  }

  private static GroupCheckoutInitiated handle(InitiateGroupCheckout command, Initial ignore) {
    return new GroupCheckoutInitiated(
        command.groupCheckoutId(), command.clerkId(), command.guestStayAccountIds(), command.now());
  }

  private static GuestCheckoutsStarted handle(
      RecordGuestCheckoutsInitiation command, InProgress ignore) {
    return new GuestCheckoutsStarted(
        command.groupCheckoutId(), command.guestStayAccountIds(), command.now());
  }

  private static GroupCheckoutEvent[] handle(
      RecordGuestCheckoutCompletion command, InProgress state) {
    var guestCheckoutCompleted =
        new GuestCheckoutCompleted(
            command.groupCheckoutId(), command.guestStayAccountId(), command.completedAt());

    return tryFinishCheckout(state, command.completedAt())
        .map(finished -> toArray(guestCheckoutCompleted, finished))
        .orElse(toArray(guestCheckoutCompleted));
  }

  private static GroupCheckoutEvent[] handle(RecordGuestCheckoutFailure command, InProgress state) {
    var guestCheckoutFailed =
        new GuestCheckoutFailed(
            command.groupCheckoutId(), command.guestStayAccountId(), command.failedAt());

    return tryFinishCheckout(state, command.failedAt())
        .map(finished -> toArray(guestCheckoutFailed, finished))
        .orElse(toArray(guestCheckoutFailed));
  }

  private static Optional<GroupCheckoutEvent> tryFinishCheckout(
      InProgress state, OffsetDateTime now) {
    if (areAnyOngoingCheckouts(state)) return Optional.empty();

    return Optional.of(
        areAnyFailedCheckouts(state)
            ? new GroupCheckoutFailed(
                state.id(),
                checkoutsWith(state, CheckoutStatus.Completed),
                checkoutsWith(state, CheckoutStatus.Failed),
                now)
            : new GroupCheckoutCompleted(
                state.id(), checkoutsWith(state, CheckoutStatus.Completed), now));
  }

  private static boolean areAnyOngoingCheckouts(InProgress state) {
    return state.guestStayCheckouts().values().stream()
        .anyMatch(
            status -> status == CheckoutStatus.Initiated || status == CheckoutStatus.InProgress);
  }

  private static boolean areAnyFailedCheckouts(InProgress state) {
    return state.guestStayCheckouts().values().stream()
        .anyMatch(status -> status == CheckoutStatus.Failed);
  }

  private static UUID[] checkoutsWith(InProgress state, CheckoutStatus status) {
    return state.guestStayCheckouts().entrySet().stream()
        .filter(entry -> entry.getValue() == status)
        .map(Map.Entry::getKey)
        .toArray(UUID[]::new);
  }
}
