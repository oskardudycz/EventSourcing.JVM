package io.eventdriven.uniqueness.users;

import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import com.eventstore.dbclient.ParseError;
import io.eventdriven.uniqueness.core.esdb.EventStore;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.esdb.ESDBResourceReservationHandler;
import io.eventdriven.uniqueness.core.retries.NulloRetryPolicy;
import io.eventdriven.uniqueness.users.reservation.UserCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static io.eventdriven.uniqueness.core.esdb.EventStore.ReadResult;
import static io.eventdriven.uniqueness.core.resourcereservation.esdb.ResourceReservationEvent.*;
import static io.eventdriven.uniqueness.core.serialization.EventSerializer.deserialize;
import static io.eventdriven.uniqueness.users.UserEvent.UserRegistered;
import static org.junit.jupiter.api.Assertions.*;

public class UserEmailRegistrationTests {
  @Test
  public void forNotUsedEmail_ReservationSucceeds() {
    var userId = UUID.randomUUID();
    var email = "%s@email.com".formatted(UUID.randomUUID().toString().replace("-", ""));

    assertTrue(userCommandHandler.registerUser(userId, email));

    var userReadResult = eventStore.read("user-%s".formatted(userId));

    assertInstanceOf(ReadResult.Success.class, userReadResult);

    if (userReadResult instanceof ReadResult.Success success) {
      var userEvents = success.events();
      assertEquals(1, userEvents.length);

      var userRegistered = deserialize(UserRegistered.class, userEvents[0]);

      assertTrue(userRegistered.isPresent());

      assertEquals(userId, userRegistered.get().userId());
      assertEquals(email, userRegistered.get().email());
    } else {
      fail();
    }

    var resourceKey = Hash.hash(email).toString();
    var reservationReadResult = eventStore.read("reservation-%s".formatted(resourceKey));

    assertInstanceOf(ReadResult.Success.class, reservationReadResult);

    if (reservationReadResult instanceof ReadResult.Success success) {
      var reservationEvents = success.events();
      assertEquals(2, reservationEvents.length);

      var reservationInitiated = deserialize(ResourceReservationInitiated.class, reservationEvents[0]);

      assertTrue(reservationInitiated.isPresent());

      assertEquals(resourceKey, reservationInitiated.get().resourceKey());
      assertEquals(reservationLockDuration, reservationInitiated.get().tentativeLockFor());

      var reservationConfirmed = deserialize(ResourceReservationConfirmed.class, reservationEvents[0]);

      assertTrue(reservationConfirmed.isPresent());

      assertEquals(resourceKey, reservationConfirmed.get().resourceKey());
    } else {
      fail();
    }

  }

  private final Duration reservationLockDuration = Duration.ofMinutes(15);
  private EventStore eventStore;
  private UserCommandHandler userCommandHandler;

  @BeforeEach
  void beforeEach() throws ParseError {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    var esdbClient = EventStoreDBClient.create(settings);
    eventStore = new EventStore(esdbClient);

    var resourceReservationCommandHandler = new ESDBResourceReservationHandler(reservationLockDuration, new NulloRetryPolicy(), eventStore);
    userCommandHandler = new UserCommandHandler(resourceReservationCommandHandler, eventStore);
  }
}
