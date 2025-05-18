package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.core.AbstractProcessManager;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts.GroupCheckoutEvent.*;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand;

public class GroupCheckout extends AbstractProcessManager<GroupCheckoutEvent, UUID> {
  private Map<UUID, CheckoutStatus> guestStayCheckouts = new LinkedHashMap<>();
  private CheckoutStatus status;

  public enum CheckoutStatus {
    INITIATED,
    COMPLETED,
    FAILED
  }

  // Default constructor for Jackson
  private GroupCheckout() {
  }

  public static GroupCheckout handle(
    GroupCheckoutCommand.InitiateGroupCheckout command
  ) {
    var groupCheckoutId = command.groupCheckoutId();
    var clerkId = command.clerkId();
    var guestStayIds = command.guestStayIds();
    var initiatedAt = command.now();

    var state = new GroupCheckout();

    state.enqueue(new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayIds, initiatedAt));

    Arrays.stream(guestStayIds)
      .map(guestAccountId -> new GuestStayAccountCommand.CheckOutGuest(guestAccountId, initiatedAt, groupCheckoutId))
      .forEach(state::schedule);

    return state;
  }

  public void on(GuestStayAccountEvent.GuestCheckedOut event) {
    var guestStayId = event.guestStayAccountId();
    var now = event.completedAt();

    if (status != CheckoutStatus.INITIATED || this.guestStayCheckouts.get(guestStayId) == CheckoutStatus.COMPLETED)
      return;

    var guestCheckoutCompleted = new GuestCheckoutCompleted(id(), guestStayId, now);

    enqueue(guestCheckoutCompleted);

    if (!areAnyOngoingCheckouts())
      enqueue(finalize(now));
  }

  public void on(GuestStayAccountEvent.GuestCheckoutFailed event) {
    var guestStayId = event.guestStayAccountId();
    var now = event.failedAt();

    if (status != CheckoutStatus.INITIATED || this.guestStayCheckouts.get(guestStayId) == CheckoutStatus.FAILED)
      return;

    var guestCheckoutFailed = new GuestCheckoutFailed(id(), guestStayId, now);

    enqueue(guestCheckoutFailed);

    if (!areAnyOngoingCheckouts())
      enqueue(finalize(now));
  }

  private GroupCheckoutEvent finalize(OffsetDateTime now) {
    return !areAnyFailedCheckouts() ?
      new GroupCheckoutCompleted(
        id(),
        checkoutsWith(CheckoutStatus.COMPLETED),
        now
      )
      :
      new GroupCheckoutFailed(
        id(),
        checkoutsWith(CheckoutStatus.COMPLETED),
        checkoutsWith(CheckoutStatus.FAILED),
        now
      );
  }

  @Override
  public void apply(GroupCheckoutEvent groupCheckoutEvent) {
    switch (groupCheckoutEvent) {
      case GroupCheckoutInitiated groupCheckoutInitiated -> {
        this.id = groupCheckoutInitiated.groupCheckoutId();
        this.guestStayCheckouts =
          Arrays.stream(groupCheckoutInitiated.guestStayAccountIds())
            .collect(Collectors.toMap(
              key -> key,
              _ -> CheckoutStatus.INITIATED,
              (existing, _) -> existing,
              LinkedHashMap::new
            ));
        this.status = CheckoutStatus.INITIATED;
      }
      case GuestCheckoutCompleted guestCheckoutCompleted -> {
        guestStayCheckouts.put(guestCheckoutCompleted.guestStayAccountId(), CheckoutStatus.COMPLETED);
      }
      case GuestCheckoutFailed guestCheckoutFailed -> {
        guestStayCheckouts.put(guestCheckoutFailed.guestStayAccountId(), CheckoutStatus.FAILED);
      }
      case GroupCheckoutCompleted groupCheckoutCompleted -> {
        this.status = CheckoutStatus.COMPLETED;
      }
      case GroupCheckoutFailed groupCheckoutFailed -> {
        this.status = CheckoutStatus.FAILED;
      }
    }
  }

  private boolean areAnyOngoingCheckouts() {
    return guestStayCheckouts.values().stream()
      .anyMatch(guestStayStatus -> guestStayStatus.equals(CheckoutStatus.INITIATED));
  }

  private boolean areAnyFailedCheckouts() {
    return guestStayCheckouts.values().stream()
      .anyMatch(guestStayStatus -> guestStayStatus.equals(CheckoutStatus.FAILED));
  }

  private UUID[] checkoutsWith(CheckoutStatus guestStayStatus) {
    return guestStayCheckouts.entrySet().stream()
      .filter(kv -> kv.getValue().equals(guestStayStatus))
      .map(Map.Entry::getKey)
      .toArray(UUID[]::new);
  }
}
