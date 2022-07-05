package io.eventdriven.uniqueness.cinematickets;

import java.time.LocalDateTime;
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
    LocalDateTime paidAt
  ) implements TicketEvent {
  }

  record SeatReservationTimedOut(
    UUID ticketId,
    LocalDateTime timedOutAt
  ) implements TicketEvent {

  }
}
