package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.groupcheckouts.GroupCheckoutEvent.*;

public record GroupCheckout(
  UUID id,
  Map<UUID, CheckoutStatus> guestStayCheckouts,
  CheckoutStatus status) {

  public enum CheckoutStatus {
    INITIATED,
    COMPLETED,
    FAILED
  }

  public static final GroupCheckout INITIAL = new GroupCheckout(
    null,
    null,
    null
  );

  public static GroupCheckout evolve(GroupCheckout state, GroupCheckoutEvent groupCheckoutEvent) {
    return switch (groupCheckoutEvent) {
      case GroupCheckoutInitiated groupCheckoutInitiated ->
        new GroupCheckout(
          groupCheckoutInitiated.groupCheckoutId(),
          Arrays.stream(groupCheckoutInitiated.guestStayAccountIds())
            .collect(Collectors.toMap(
              key -> key,
              _ -> CheckoutStatus.INITIATED,
              (existing, _) -> existing,
              LinkedHashMap::new
            )),
          CheckoutStatus.INITIATED
        );
      case GuestCheckoutCompleted guestCheckoutCompleted -> {
        var guestStayCheckouts = new LinkedHashMap<>(state.guestStayCheckouts());
        guestStayCheckouts.put(guestCheckoutCompleted.guestStayAccountId(), CheckoutStatus.COMPLETED);

        yield new GroupCheckout(
          state.id(),
          guestStayCheckouts,
          state.status()
        );
      }
      case GuestCheckoutFailed guestCheckoutFailed -> {
        var guestStayCheckouts = new LinkedHashMap<>(state.guestStayCheckouts());
        guestStayCheckouts.put(guestCheckoutFailed.guestStayAccountId(), CheckoutStatus.FAILED);

        yield new GroupCheckout(
          state.id(),
          guestStayCheckouts,
          state.status()
        );
      }
      case GroupCheckoutCompleted groupCheckoutCompleted -> new GroupCheckout(
        state.id(),
        state.guestStayCheckouts(),
        CheckoutStatus.COMPLETED
      );
      case GroupCheckoutFailed groupCheckoutFailed -> new GroupCheckout(
        state.id(),
        state.guestStayCheckouts(),
        CheckoutStatus.FAILED
      );
    };
  }

  public boolean recordedAlreadyGuestCheckoutWithStatus(UUID guestStayId, CheckoutStatus status) {
    return guestStayCheckouts().get(guestStayId) == status;
  }

  public boolean areAnyOngoingCheckouts() {
    return guestStayCheckouts.values().stream()
      .anyMatch(guestStayStatus -> guestStayStatus.equals(CheckoutStatus.INITIATED));
  }

  public boolean areAnyFailedCheckouts() {
    return guestStayCheckouts.values().stream()
      .anyMatch(guestStayStatus -> guestStayStatus.equals(CheckoutStatus.FAILED));
  }

  public UUID[] checkoutsWith(CheckoutStatus guestStayStatus) {
    return guestStayCheckouts.entrySet().stream()
      .filter(kv -> kv.getValue().equals(guestStayStatus))
      .map(Map.Entry::getKey)
      .toArray(UUID[]::new);
  }
}
