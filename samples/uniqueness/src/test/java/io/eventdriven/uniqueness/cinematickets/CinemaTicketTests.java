package io.eventdriven.uniqueness.cinematickets;

import com.eventstore.dbclient.*;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.serialization.EventSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.eventdriven.uniqueness.cinematickets.TicketEvent.SeatReserved;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class CinemaTicketTests {
  @Test
  public void EnforcesUniqueness_WithHashedStreamId() throws ExecutionException, InterruptedException {
    var clientId = UUID.randomUUID();
    var screeningId = UUID.randomUUID();
    var seatId = UUID.randomUUID();
    var price = 123;
    // We're assuming that there can be only a single seat reservation for specific screening.
    // We can enforce uniqueness by putting either both screeningId and seatId into a stream id
    // or use unique hash from combined values
    var ticketId = Hash.hash("%s_%s".formatted(screeningId, seatId)).toString();
    var ticketStreamId = "cinema_ticket-%s".formatted(ticketId);
    var seatReserved = new SeatReserved(ticketId, clientId, screeningId, seatId, price);

    // This one should succeed as we don't have such stream yet
    eventStore.appendToStream(
      ticketStreamId,
      AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
      EventSerializer.serialize(seatReserved)
    ).get();

    // This one will fail, as we're expecting that stream doesn't exist
    // Let's also regenerate hash, just in case
    ticketId = Hash.hash("%s_%s".formatted(screeningId, seatId)).toString();
    ticketStreamId = "cinema_ticket-%s".formatted(ticketId);

    try {
      eventStore.appendToStream(
        ticketStreamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
        EventSerializer.serialize(seatReserved)
      ).get();
    } catch (ExecutionException exception) {
      assertInstanceOf(WrongExpectedVersionException.class, exception.getCause());
    }
  }

  private EventStoreDBClient eventStore;

  @BeforeEach
  void beforeEach() throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);
  }
}
