package io.eventdriven.uniqueness.cinematickets;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TicketEvent {
  record SeatReserved(
    UUID ticketId,
    UUID clientId,
    UUID screeningId,
    UUID seatId,
    double price
  ) implements TicketEvent {
  }

  record TicketPaid(
    String ticketId,
    OffsetDateTime paidAt
  ) implements TicketEvent {
  }

  record SeatReservationTimedOut(
    UUID ticketId,
    OffsetDateTime timedOutAt
  ) implements TicketEvent {

  }
}
