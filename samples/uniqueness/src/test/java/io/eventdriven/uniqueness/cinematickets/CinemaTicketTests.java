package io.eventdriven.uniqueness.cinematickets;

import com.eventstore.dbclient.*;
import io.eventdriven.uniqueness.core.async.SyncProcessor;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static io.eventdriven.uniqueness.cinematickets.TicketEvent.SeatReservationTimedOut;
import static io.eventdriven.uniqueness.cinematickets.TicketEvent.SeatReserved;
import static io.eventdriven.uniqueness.core.serialization.EventSerializer.serialize;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class CinemaTicketTests {
  @Test
  public void enforcesUniqueness_WithHashedStreamId() throws ExecutionException, InterruptedException {
    // We're assuming that there can be only a single seat reservation for specific screening.
    // We can enforce uniqueness by putting either both screeningId and seatId into a stream id
    // or use unique hash from combined values
    var seatReservationId = "cinema_ticket-%s".formatted(
      Hash.hash("%s_%s".formatted(screeningId, seatId)).toString()
    );

    // This one should succeed as we don't have such stream yet
    eventStore.appendToStream(
      seatReservationId,
      AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
      serialize(new SeatReserved(ticketId, clientId, screeningId, seatId, price))
    ).get();


    // This one will fail, as we're expecting that stream doesn't exist
    try {
      var otherTicketId = UUID.randomUUID();
      var otherClientId = UUID.randomUUID();

      eventStore.appendToStream(
        seatReservationId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
        serialize(new SeatReserved(otherTicketId, otherClientId, screeningId, seatId, price))
      ).get();
    } catch (ExecutionException exception) {
      assertInstanceOf(WrongExpectedVersionException.class, exception.getCause());
    }
  }

  @Test
  public void releaseReservation_WillAllowReservingAgain() throws ExecutionException, InterruptedException {
    var seatReservationId = "cinema_ticket-%s".formatted(
      Hash.hash("%s_%s".formatted(screeningId, seatId)).toString()
    );
    var reservationTimedOut = serialize(new SeatReservationTimedOut(ticketId, OffsetDateTime.now()));

    // We're simulating reservation that timed out because of e.g. not paying for it
    var nextExpectedRevision = eventStore.appendToStream(
      seatReservationId,
      AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
      serialize(new SeatReserved(ticketId, clientId, screeningId, seatId, price)),
      reservationTimedOut
    ).get().getNextExpectedRevision();


    // We're running subscription synchronously to simplify testing
    SyncProcessor.runSync((ack) ->
      {
        try {

          // We'll use subscription, as we want to be sure that we'll release the reservation.
          // We'll subscribe only for the reservationTimedOut event, as it triggers release
          var filterReservationTimedOut = SubscribeToAllOptions.get()
            .filter(SubscriptionFilter.newBuilder().addEventTypePrefix(reservationTimedOut.getEventType()).build());
          eventStore.subscribeToAll(new SubscriptionListener() {
            @Override
            public void onEvent(Subscription subscription, ResolvedEvent resolvedEvent) {
              // wait for the SeatReserved event we appended above
              if (!resolvedEvent.getEvent().getEventId().equals(reservationTimedOut.getEventId()))
                return;

              try {
                // Make soft delete of the stream, it'll allow streamId to be reused
                // SeatReservationTimedOut will be used as "tombstone event"
                // that marks where previous reservation has finished
                eventStore.deleteStream(
                  seatReservationId,
                  DeleteStreamOptions.get().expectedRevision(nextExpectedRevision)
                ).get();

                // finish processing
                ack.accept(true);
                subscription.stop();
              } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
              }
            }
          }, filterReservationTimedOut).get();
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    );

    // This one should now succeed as we don't have such stream anymore
    var otherTicketId = UUID.randomUUID();
    var otherClientId = UUID.randomUUID();

    eventStore.appendToStream(
      seatReservationId,
      AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
      serialize(new SeatReserved(otherTicketId, otherClientId, screeningId, seatId, price))
    ).get();
  }

  private static final Logger logger = LoggerFactory.getLogger(CinemaTicketTests.class);
  private EventStoreDBClient eventStore;

  private UUID ticketId;
  private UUID clientId;
  private UUID screeningId;
  private UUID seatId;
  private double price = 123;

  @BeforeEach
  void beforeEach() throws ConnectionStringParsingException {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    this.eventStore = EventStoreDBClient.create(settings);

    ticketId = UUID.randomUUID();
    clientId = UUID.randomUUID();
    screeningId = UUID.randomUUID();
    seatId = UUID.randomUUID();
  }
}
