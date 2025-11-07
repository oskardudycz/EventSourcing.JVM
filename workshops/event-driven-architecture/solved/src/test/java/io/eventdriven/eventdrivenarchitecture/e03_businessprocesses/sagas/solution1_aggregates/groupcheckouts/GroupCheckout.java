package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.AbstractAggregate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutEvent.*;
import static java.util.stream.Collectors.toMap;

public class GroupCheckout extends AbstractAggregate<GroupCheckoutEvent, UUID> {
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

  public static GroupCheckout initiate(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayIds,
    OffsetDateTime initiatedAt
  ) {
    var checkOut = new GroupCheckout();

    checkOut.enqueue(new GroupCheckoutInitiated(groupCheckoutId, clerkId, guestStayIds, initiatedAt));

    return checkOut;
  }

  public void recordGuestCheckoutCompletion(
    UUID guestStayId,
    OffsetDateTime now
  ) {
    if (status != CheckoutStatus.INITIATED || this.guestStayCheckouts.get(guestStayId) == CheckoutStatus.COMPLETED)
      return;

    var guestCheckoutCompleted = new GuestCheckoutCompletionRecorded(id(), guestStayId, now);

    enqueue(guestCheckoutCompleted);

    if (!areAnyOngoingCheckouts())
      enqueue(finalize(now));
  }

  public void recordGuestCheckoutFailure(
    UUID guestStayId,
    OffsetDateTime now
  ) {
    if (status != CheckoutStatus.INITIATED || this.guestStayCheckouts.get(guestStayId) == CheckoutStatus.FAILED)
      return;

    var guestCheckoutFailed = new GuestCheckoutFailureRecorded(id(), guestStayId, now);

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
    switch (groupCheckoutEvent){
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
      case GuestCheckoutCompletionRecorded guestCheckoutCompleted -> {
        guestStayCheckouts.put(guestCheckoutCompleted.guestStayAccountId(), CheckoutStatus.COMPLETED);
      }
      case GuestCheckoutFailureRecorded guestCheckoutFailed -> {
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
