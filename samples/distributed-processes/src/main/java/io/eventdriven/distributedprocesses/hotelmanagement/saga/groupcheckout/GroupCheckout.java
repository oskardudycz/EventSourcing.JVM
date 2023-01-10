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
}
