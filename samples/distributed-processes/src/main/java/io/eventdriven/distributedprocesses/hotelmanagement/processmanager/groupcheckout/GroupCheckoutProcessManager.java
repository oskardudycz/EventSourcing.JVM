package io.eventdriven.distributedprocesses.hotelmanagement.processmanager.groupcheckout;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.valueIs;
import static io.eventdriven.distributedprocesses.hotelmanagement.processmanager.groupcheckout.GroupCheckoutEvent.GroupCheckoutInitiated;
import static io.eventdriven.distributedprocesses.hotelmanagement.processmanager.gueststayaccount.GuestStayAccountCommand.CheckOutGuest;
import static java.util.stream.Collectors.toMap;

import io.eventdriven.distributedprocesses.core.processes.AbstractProcessManager;
import io.eventdriven.distributedprocesses.hotelmanagement.processmanager.gueststayaccount.GuestStayAccountEvent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class GroupCheckoutProcessManager extends AbstractProcessManager<UUID> {
  private Map<UUID, CheckoutStatus> guestStayCheckouts;

  private CheckoutStatus status;

  private OffsetDateTime completedAt;
  private OffsetDateTime failedAt;

  private GroupCheckoutProcessManager(
      UUID id, Map<UUID, CheckoutStatus> guestStayCheckouts, CheckoutStatus status) {
    this.id = id;
    this.guestStayCheckouts = guestStayCheckouts;
    this.status = status;
  }

  enum CheckoutStatus {
    Initiated,
    InProgress,
    Completed,
    Failed
  }

  public static GroupCheckoutProcessManager initiate(UUID id, UUID[] guestStayAccountIds) {
    return new GroupCheckoutProcessManager(
        id,
        Arrays.stream(guestStayAccountIds)
            .collect(toMap(key -> key, value -> CheckoutStatus.Initiated)),
        CheckoutStatus.Initiated);
  }

  public void on(GroupCheckoutInitiated checkoutInitiated) {
    for (var guestAccountId : checkoutInitiated.guestStayAccountIds()) {
      schedule(new CheckOutGuest(
          guestAccountId, checkoutInitiated.groupCheckoutId(), checkoutInitiated.initiatedAt()));
    }

    for (var guestStayAccountId : checkoutInitiated.guestStayAccountIds()) {
      if (valueIs(guestStayCheckouts, guestStayAccountId, CheckoutStatus.Initiated))
        guestStayCheckouts.replace(guestStayAccountId, CheckoutStatus.InProgress);
    }
  }

  public void on(GuestStayAccountEvent.GuestCheckedOut checkoutCompleted) {
    if (checkoutCompleted.groupCheckoutId() == null
        || status == CheckoutStatus.Completed
        || status == CheckoutStatus.Failed) return;

    var guestStayAccountId = checkoutCompleted.guestStayAccountId();

    if (valueIs(
        guestStayCheckouts,
        guestStayAccountId,
        CheckoutStatus.Initiated,
        CheckoutStatus.InProgress))
      guestStayCheckouts.replace(guestStayAccountId, CheckoutStatus.Completed);

    tryFinishCheckout(checkoutCompleted.completedAt());
  }

  public void on(GuestStayAccountEvent.GuestCheckoutFailed checkoutFailed) {
    if (checkoutFailed.groupCheckoutId() == null
        || status == CheckoutStatus.Completed
        || status == CheckoutStatus.Failed) return;

    var guestStayAccountId = checkoutFailed.guestStayAccountId();

    if (valueIs(
        guestStayCheckouts,
        guestStayAccountId,
        CheckoutStatus.Initiated,
        CheckoutStatus.InProgress))
      guestStayCheckouts.replace(guestStayAccountId, CheckoutStatus.Completed);

    tryFinishCheckout(checkoutFailed.failedAt());
  }

  private void tryFinishCheckout(OffsetDateTime now) {
    if (areAnyOngoingCheckouts()) return;

    if (areAnyFailedCheckouts()) {
      status = CheckoutStatus.Failed;
      failedAt = now;
    } else {
      status = CheckoutStatus.Completed;
      completedAt = now;
    }
  }

  private boolean areAnyOngoingCheckouts() {
    return guestStayCheckouts.values().stream()
        .anyMatch(
            status -> status == CheckoutStatus.Initiated || status == CheckoutStatus.InProgress);
  }

  private boolean areAnyFailedCheckouts() {
    return guestStayCheckouts.values().stream().anyMatch(status -> status == CheckoutStatus.Failed);
  }
}
