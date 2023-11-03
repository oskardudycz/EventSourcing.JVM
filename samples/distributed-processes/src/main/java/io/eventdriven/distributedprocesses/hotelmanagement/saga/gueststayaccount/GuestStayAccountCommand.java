package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.distributedprocesses.core.http.ETag;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.lang.Nullable;

public sealed interface GuestStayAccountCommand {
  record CheckInGuest(UUID guestStayAccountId, ETag expectedRevision, OffsetDateTime now)
      implements GuestStayAccountCommand {}

  record RecordCharge(
      UUID guestStayAccountId, double amount, ETag expectedRevision, OffsetDateTime now)
      implements GuestStayAccountCommand {}

  record RecordPayment(
      UUID guestStayAccountId, double amount, ETag expectedRevision, OffsetDateTime now)
      implements GuestStayAccountCommand {}

  record CheckOutGuest(UUID guestStayAccountId, @Nullable UUID groupCheckoutId, OffsetDateTime now)
      implements GuestStayAccountCommand {}
}
