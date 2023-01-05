package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static java.util.stream.Collectors.toMap;

public class GroupCheckout extends AbstractAggregate<GroupCheckoutEvent, UUID> {
  private enum CheckoutStatus {
    Pending,
    Initiated,
    Completed,
    Failed
  }

  private Map<UUID, CheckoutStatus> guestStayCheckouts;
  private CheckoutStatus status = CheckoutStatus.Pending;

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
    enqueue(new GroupCheckoutEvent.GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayAccountIds, initiatedAt));
  }

  public void recordGuestStaysCheckoutInitiation(UUID[] initiatedCheckouts, OffsetDateTime initiatedAt) {
    if (status != CheckoutStatus.Initiated)
      throw new IllegalStateException("Cannot record guest stay if status is other than Initiated");

    enqueue(new GroupCheckoutEvent.GuestCheckoutsInitiated(id(), initiatedCheckouts, initiatedAt));
  }

  public void recordGuestStayCheckoutCompletion(UUID completedCheckout, OffsetDateTime completedAt) {
    if (status != CheckoutStatus.Initiated)
      throw new IllegalStateException("Cannot record guest stay if status is other than Initiated");

    enqueue(new GroupCheckoutEvent.GuestCheckoutCompleted(id(), completedCheckout, completedAt));

    tryFinishCheckout(completedAt);
  }

  public void recordGuestStayCheckoutFailure(UUID failedCheckout, OffsetDateTime failedAt) {
    if (status != CheckoutStatus.Initiated)
      throw new IllegalStateException("Cannot record guest stay if status is other than Initiated");

    enqueue(new GroupCheckoutEvent.GuestCheckoutCompleted(id(), failedCheckout, failedAt));

    tryFinishCheckout(failedAt);
  }

  @Override
  public void when(GroupCheckoutEvent event) {
    switch (event) {
      case GroupCheckoutEvent.GroupCheckoutInitiated checkoutInitiated -> {
        id = checkoutInitiated.groupCheckoutId();
        guestStayCheckouts = Arrays.stream(checkoutInitiated.guestStayAccountIds())
          .collect(toMap(key -> key, value -> CheckoutStatus.Pending));
        status = CheckoutStatus.Pending;
      }
      case GroupCheckoutEvent.GuestCheckoutsInitiated guestCheckoutInitiated -> {
        for (var guestStayCheckoutId : guestCheckoutInitiated.guestStayAccountIds()) {
          guestStayCheckouts.replace(guestStayCheckoutId, CheckoutStatus.Initiated);
        }
      }
      case GroupCheckoutEvent.GuestCheckoutCompleted guestCheckoutCompleted ->
        guestStayCheckouts.replace(guestCheckoutCompleted.guestStayAccountId(), CheckoutStatus.Completed);
      case GroupCheckoutEvent.GuestCheckoutFailed guestCheckoutFailed ->
        guestStayCheckouts.replace(guestCheckoutFailed.guestStayAccountId(), CheckoutStatus.Failed);
      case GroupCheckoutEvent.GroupCheckoutCompleted groupCheckoutCompleted ->
        status = CheckoutStatus.Completed;
      case GroupCheckoutEvent.GroupCheckoutFailed groupCheckoutFailed ->
        status = CheckoutStatus.Failed;
    }
  }

  private void tryFinishCheckout(OffsetDateTime now) {
    if (areAnyOngoingCheckouts()) {
      return;
    }
    if (areAnyFailedCheckouts()) {
      enqueue(new GroupCheckoutEvent.GroupCheckoutFailed(id(), checkoutsWith(CheckoutStatus.Completed), checkoutsWith(CheckoutStatus.Failed), now));
      return;
    }
    enqueue(new GroupCheckoutEvent.GroupCheckoutCompleted(id(), checkoutsWith(CheckoutStatus.Completed), now));
  }

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
}
