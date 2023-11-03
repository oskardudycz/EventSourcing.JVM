package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.valueIs;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckoutEvent.*;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public sealed interface GroupCheckout {
  record Initial() implements GroupCheckout {}

  record InProgress(UUID id, Map<UUID, CheckoutStatus> guestStayCheckouts)
      implements GroupCheckout {}

  record Completed(UUID id) implements GroupCheckout {}

  record Failed(UUID id) implements GroupCheckout {}

  enum CheckoutStatus {
    Initiated,
    InProgress,
    Completed,
    Failed
  }

  static GroupCheckout evolve(GroupCheckout current, GroupCheckoutEvent event) {
    return switch (event) {
      case GroupCheckoutInitiated initiated: {
        if (!(current instanceof Initial)) yield current;

        yield new InProgress(
            initiated.groupCheckoutId(),
            Arrays.stream(initiated.guestStayAccountIds())
                .collect(toMap(key -> key, value -> CheckoutStatus.Initiated)));
      }
      case GuestCheckoutsStarted checkoutsStarted: {
        if (!(current instanceof InProgress inProgress)) yield current;

        var guestStayCheckouts = new HashMap<>(inProgress.guestStayCheckouts);

        for (var guestStayAccountId : checkoutsStarted.guestStayAccountIds()) {
          if (valueIs(guestStayCheckouts, guestStayAccountId, CheckoutStatus.Initiated))
            guestStayCheckouts.replace(guestStayAccountId, CheckoutStatus.InProgress);
        }

        yield new InProgress(inProgress.id(), guestStayCheckouts);
      }
      case GuestCheckoutCompleted checkoutCompleted: {
        if (!(current instanceof InProgress inProgress)) yield current;

        var guestStayCheckouts = new HashMap<>(inProgress.guestStayCheckouts);
        var guestStayAccountId = checkoutCompleted.guestStayAccountId();

        if (valueIs(
            guestStayCheckouts,
            guestStayAccountId,
            CheckoutStatus.Initiated,
            CheckoutStatus.InProgress))
          guestStayCheckouts.replace(guestStayAccountId, CheckoutStatus.Completed);

        yield new InProgress(inProgress.id(), guestStayCheckouts);
      }
      case GuestCheckoutFailed guestCheckoutFailed: {
        if (!(current instanceof InProgress inProgress)) yield current;

        var guestStayCheckouts = new HashMap<>(inProgress.guestStayCheckouts);
        var guestStayAccountId = guestCheckoutFailed.guestStayAccountId();

        if (valueIs(
            guestStayCheckouts,
            guestStayAccountId,
            CheckoutStatus.Initiated,
            CheckoutStatus.InProgress))
          guestStayCheckouts.replace(guestStayAccountId, CheckoutStatus.Failed);

        yield new InProgress(inProgress.id(), guestStayCheckouts);
      }
      case GroupCheckoutCompleted ignore: {
        if (!(current instanceof InProgress inProgress)) yield current;

        yield new Completed(inProgress.id());
      }
      case GroupCheckoutFailed ignore: {
        if (!(current instanceof InProgress inProgress)) yield current;

        yield new Failed(inProgress.id());
      }
    };
  }
}
