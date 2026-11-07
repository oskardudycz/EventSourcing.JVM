package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public record GroupCheckout(
  UUID id,
  Map<UUID, CheckoutStatus> guestStayCheckouts,
  OffsetDateTime updatedAt
) {
  public enum CheckoutStatus {
    INITIATED,
    COMPLETED,
    FAILED
  }

  public static GroupCheckout initiated(UUID id, UUID[] guestStayIds, OffsetDateTime initiatedAt) {
    return new GroupCheckout(
      id,
      Arrays.stream(guestStayIds)
        .collect(Collectors.toMap(
          key -> key,
          _ -> CheckoutStatus.INITIATED,
          (existing, _) -> existing,
          LinkedHashMap::new
        )),
      initiatedAt
    );
  }

  public GroupCheckout withCheckout(UUID guestStayId, CheckoutStatus status, OffsetDateTime at) {
    var checkouts = new LinkedHashMap<>(guestStayCheckouts);
    checkouts.put(guestStayId, status);

    return new GroupCheckout(id, checkouts, at);
  }

  public Optional<UUID> nextGuestToCheckOut() {
    return guestStayCheckouts.entrySet().stream()
      .filter(kv -> kv.getValue().equals(CheckoutStatus.INITIATED))
      .map(Map.Entry::getKey)
      .findFirst();
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
