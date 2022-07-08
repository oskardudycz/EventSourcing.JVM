package io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout.GroupCheckoutEvent.*;
import static java.util.stream.Collectors.toMap;

public class GroupCheckout extends AbstractAggregate<GroupCheckoutEvent, UUID> {
  private Map<UUID, CheckoutStatus> guestStayCheckouts;
  private CheckoutStatus status = CheckoutStatus.Pending;

  private boolean areAnyOngoingCheckouts() {
    return guestStayCheckouts.values().stream()
      .anyMatch(status -> status == CheckoutStatus.Initiated || status == CheckoutStatus.Pending);
  }

  private boolean areAnyFailedCheckouts() {
    return guestStayCheckouts.values().stream()
      .anyMatch(status -> status == CheckoutStatus.Failed);
  }

  private UUID[] checkoutsWith(CheckoutStatus status) {
    return guestStayCheckouts.entrySet().stream()
      .filter(entry -> entry.getValue() == status)
      .map(Map.Entry::getKey)
      .toArray(UUID[]::new);
  }

  public static GroupCheckout initiate(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayAccountIds,
    OffsetDateTime initiatedAt
  ) {
    return new GroupCheckout(
      groupCheckoutId,
      clerkId,
      guestStayAccountIds,
      initiatedAt
    );
  }

  private GroupCheckout(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayAccountIds,
    OffsetDateTime initiatedAt
  ) {
    enqueue(new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayAccountIds, initiatedAt));
  }

  public void recordGuestStaysCheckoutInitiation(UUID[] initiatedCheckouts, OffsetDateTime initiatedAt) {
    if (status != CheckoutStatus.Initiated)
      throw new RuntimeException("Cannot record guest stay if status is other than Initiated");

    enqueue(new GuestCheckoutsInitiated(id(), initiatedCheckouts, initiatedAt));
  }

  public void recordGuestStayCheckoutCompletion(UUID completedCheckout, OffsetDateTime completedAt) {
    if (status != CheckoutStatus.Initiated)
      throw new RuntimeException("Cannot record guest stay if status is other than Initiated");

    enqueue(new GuestCheckoutCompleted(id(), completedCheckout, completedAt));

    tryFinishCheckout(completedAt);
  }

  public void recordGuestStayCheckoutFailure(UUID failedCheckout, OffsetDateTime failedAt) {
    if (status != CheckoutStatus.Initiated)
      throw new RuntimeException("Cannot record guest stay if status is other than Initiated");

    enqueue(new GuestCheckoutCompleted(id(), failedCheckout, failedAt));

    tryFinishCheckout(failedAt);
  }

  private void tryFinishCheckout(OffsetDateTime now) {
    if (areAnyOngoingCheckouts()) {
      return;
    }
    if (areAnyFailedCheckouts()) {
      enqueue(new GroupCheckoutFailed(id(), checkoutsWith(CheckoutStatus.Completed), checkoutsWith(CheckoutStatus.Failed), now));
      return;
    }
    enqueue(new GroupCheckoutCompleted(id(), checkoutsWith(CheckoutStatus.Completed), checkoutsWith(CheckoutStatus.Failed), now));
  }

  @Override
  public void when(GroupCheckoutEvent event) {
    switch (event) {
      case GroupCheckoutInitiated checkoutInitiated -> {
        id = checkoutInitiated.groupCheckoutId();
        guestStayCheckouts = Arrays.stream(checkoutInitiated.guestStayAccountIds())
          .collect(toMap(key -> key, value -> CheckoutStatus.Pending));
        status = CheckoutStatus.Pending;
      }
      case GuestCheckoutsInitiated guestCheckoutInitiated -> {
        for (var guestStayCheckoutId : guestCheckoutInitiated.guestStayAccountIds()) {
          guestStayCheckouts.replace(guestStayCheckoutId, CheckoutStatus.Initiated);
        }
      }
      case GuestCheckoutCompleted guestCheckoutCompleted -> {
        guestStayCheckouts.replace(guestCheckoutCompleted.guestStayAccountId(), CheckoutStatus.Completed);
      }
      case GuestCheckoutFailed guestCheckoutFailed -> {
        guestStayCheckouts.replace(guestCheckoutFailed.guestStayAccountId(), CheckoutStatus.Failed);
      }
      case GroupCheckoutCompleted groupCheckoutCompleted -> {
        status = CheckoutStatus.Completed;
      }
      case GroupCheckoutFailed groupCheckoutFailed -> {
        status = CheckoutStatus.Failed;
      }
    }
  }
}
