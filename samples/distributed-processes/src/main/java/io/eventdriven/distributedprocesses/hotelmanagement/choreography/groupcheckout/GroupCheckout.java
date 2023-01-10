package io.eventdriven.distributedprocesses.hotelmanagement.choreography.groupcheckout;

import java.util.Map;
import java.util.UUID;

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
