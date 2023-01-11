package io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.valueIs;
import static java.util.stream.Collectors.toMap;
import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout.GroupCheckoutEvent.*;

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

  static GroupCheckout evolve(GroupCheckout current, GroupCheckoutEvent event) {
    return switch (event) {
      case GroupCheckoutInitiated initiated: {
        if (!(current instanceof GroupCheckout.Initial))
          yield current;

        yield new GroupCheckout.InProgress(
          initiated.groupCheckoutId(),
          Arrays.stream(initiated.guestStayAccountIds())
            .collect(toMap(key -> key, value -> GroupCheckout.CheckoutStatus.Initiated))
        );
      }
      case GuestCheckoutsStarted checkoutsStarted: {
        if (!(current instanceof GroupCheckout.InProgress inProgress))
          yield current;

        var guestStayCheckouts = new HashMap<>(inProgress.guestStayCheckouts);

        for (var guestStayAccountId : checkoutsStarted.guestStayAccountIds()) {
          if (valueIs(guestStayCheckouts, guestStayAccountId, GroupCheckout.CheckoutStatus.Initiated))
            guestStayCheckouts.replace(guestStayAccountId, GroupCheckout.CheckoutStatus.InProgress);
        }

        yield new GroupCheckout.InProgress(
          inProgress.id(),
          guestStayCheckouts
        );
      }
      case GuestCheckoutCompleted checkoutCompleted: {
        if (!(current instanceof GroupCheckout.InProgress inProgress))
          yield current;

        var guestStayCheckouts = new HashMap<>(inProgress.guestStayCheckouts);
        var guestStayAccountId = checkoutCompleted.guestStayAccountId();

        if (valueIs(guestStayCheckouts, guestStayAccountId, GroupCheckout.CheckoutStatus.Initiated, GroupCheckout.CheckoutStatus.InProgress))
          guestStayCheckouts.replace(guestStayAccountId, GroupCheckout.CheckoutStatus.Completed);

        yield new GroupCheckout.InProgress(
          inProgress.id(),
          guestStayCheckouts
        );
      }
      case GuestCheckoutFailed guestCheckoutFailed: {
        if (!(current instanceof GroupCheckout.InProgress inProgress))
          yield current;

        var guestStayCheckouts = new HashMap<>(inProgress.guestStayCheckouts);
        var guestStayAccountId = guestCheckoutFailed.guestStayAccountId();

        if (valueIs(guestStayCheckouts, guestStayAccountId, GroupCheckout.CheckoutStatus.Initiated, GroupCheckout.CheckoutStatus.InProgress))
          guestStayCheckouts.replace(guestStayAccountId, GroupCheckout.CheckoutStatus.Failed);

        yield new GroupCheckout.InProgress(
          inProgress.id(),
          guestStayCheckouts
        );
      }
      case GroupCheckoutCompleted ignore: {
        if (!(current instanceof GroupCheckout.InProgress inProgress))
          yield current;

        yield new GroupCheckout.Completed(
          inProgress.id()
        );
      }
      case GroupCheckoutFailed ignore: {
        if (!(current instanceof GroupCheckout.InProgress inProgress))
          yield current;

        yield new GroupCheckout.Failed(
          inProgress.id()
        );
      }
    };
  }
}
