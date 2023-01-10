package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import java.util.Map;
import java.util.UUID;

import static java.util.stream.Collectors.toMap;

public sealed interface GroupCheckout {
  record Initial() implements GroupCheckout {
  }

  record InProgress(
    UUID id,
    Map<UUID, CheckoutStatus> guestStayCheckouts
  ) implements GroupCheckout {
  }

  record Completed(
    UUID id
  ) implements GroupCheckout {
  }

  record Failed(
    UUID id
  ) implements GroupCheckout {
  }

  enum CheckoutStatus {
    Initiated,
    InProgress,
    Completed,
    Failed
  }
}
//
//  public void recordGuestStayCheckoutCompletion(UUID completedCheckout, OffsetDateTime completedAt) {
//    if (status != CheckoutStatus.Initiated)
//      throw new IllegalStateException("Cannot record guest stay if status is other than Initiated");
//
//    enqueue(new GroupCheckoutEvent.GuestCheckoutCompleted(id(), completedCheckout, completedAt));
//
//    tryFinishCheckout(completedAt);
//  }
//
//  public void recordGuestStayCheckoutFailure(UUID failedCheckout, OffsetDateTime failedAt) {
//    if (status != CheckoutStatus.Initiated)
//      throw new IllegalStateException("Cannot record guest stay if status is other than Initiated");
//
//    enqueue(new GroupCheckoutEvent.GuestCheckoutCompleted(id(), failedCheckout, failedAt));
//
//    tryFinishCheckout(failedAt);
//  }
//
//  @Override
//  public void when(GroupCheckoutEvent event) {
//    switch (event) {
//      case GroupCheckoutEvent.GroupCheckoutInitiated checkoutInitiated -> {
//        id = checkoutInitiated.groupCheckoutId();
//        guestStayCheckouts = Arrays.stream(checkoutInitiated.guestStayAccountIds())
//          .collect(toMap(key -> key, value -> CheckoutStatus.Pending));
//        status = CheckoutStatus.Pending;
//      }
//      case GroupCheckoutEvent.GuestCheckoutsInitiated guestCheckoutInitiated -> {
//        for (var guestStayCheckoutId : guestCheckoutInitiated.guestStayAccountIds()) {
//          guestStayCheckouts.replace(guestStayCheckoutId, CheckoutStatus.Initiated);
//        }
//      }
//      case GroupCheckoutEvent.GuestCheckoutCompleted guestCheckoutCompleted ->
//        guestStayCheckouts.replace(guestCheckoutCompleted.guestStayAccountId(), CheckoutStatus.Completed);
//      case GroupCheckoutEvent.GuestCheckoutFailed guestCheckoutFailed ->
//        guestStayCheckouts.replace(guestCheckoutFailed.guestStayAccountId(), CheckoutStatus.Failed);
//      case GroupCheckoutEvent.GroupCheckoutCompleted groupCheckoutCompleted ->
//        status = CheckoutStatus.Completed;
//      case GroupCheckoutEvent.GroupCheckoutFailed groupCheckoutFailed ->
//        status = CheckoutStatus.Failed;
//    }
//  }
//
//  private void tryFinishCheckout(OffsetDateTime now) {
//    if (areAnyOngoingCheckouts()) {
//      return;
//    }
//    if (areAnyFailedCheckouts()) {
//      enqueue(new GroupCheckoutEvent.GroupCheckoutFailed(id(), checkoutsWith(CheckoutStatus.Completed), checkoutsWith(CheckoutStatus.Failed), now));
//      return;
//    }
//    enqueue(new GroupCheckoutEvent.GroupCheckoutCompleted(id(), checkoutsWith(CheckoutStatus.Completed), now));
//  }
//
//  private boolean areAnyOngoingCheckouts() {
//    return guestStayCheckouts.values().stream()
//      .anyMatch(status -> status == CheckoutStatus.Initiated || status == CheckoutStatus.Pending);
//  }
//
//  private boolean areAnyFailedCheckouts() {
//    return guestStayCheckouts.values().stream()
//      .anyMatch(status -> status == CheckoutStatus.Failed);
//  }
//
//  private UUID[] checkoutsWith(CheckoutStatus status) {
//    return guestStayCheckouts.entrySet().stream()
//      .filter(entry -> entry.getValue() == status)
//      .map(Map.Entry::getKey)
//      .toArray(UUID[]::new);
//  }
// }
