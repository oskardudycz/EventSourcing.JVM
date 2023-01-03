package io.eventdriven.uniqueness.users;

import com.eventstore.dbclient.ConnectionStringParsingException;
import com.eventstore.dbclient.EventStoreDBClient;
import com.eventstore.dbclient.EventStoreDBClientSettings;
import com.eventstore.dbclient.EventStoreDBConnectionString;
import io.eventdriven.uniqueness.core.esdb.EventStore;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.esdb.ESDBResourceReservationHandler;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservation;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservationRepository;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservationScavenging;
import io.eventdriven.uniqueness.core.retries.NulloRetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;


import static io.eventdriven.uniqueness.core.resourcereservation.esdb.ResourceReservationEvent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@RunWith(SpringRunner.class)
public class UserEmailRegistrationScavengingTests {

  @Test
  public void scavenging_cleansReservationAndLookup() {
    // Given
    for (var reservation : toScavenge) {
      var resourceKey = reservation.getResourceKey();

      repository.save(reservation);
      eventStore.append(getReservationStreamId(resourceKey),
        new ResourceReservationInitiated(resourceKey, reservation.getInitiatedAt(), Duration.ofMinutes(1))
      );
    }

    for (var reservation : toKeep) {
      var resourceKey = reservation.getResourceKey();

      repository.save(reservation);

      eventStore.append(getReservationStreamId(resourceKey),
        new ResourceReservationInitiated(resourceKey, reservation.getInitiatedAt(), Duration.ofMinutes(1)),
        new ResourceReservationConfirmed(resourceKey, reservation.getReservedAt())
      );
    }

    // When
    resourceReservationScavenging.scavengeTimedOut(OffsetDateTime.now());

    // Then
    for (var reservation : toScavenge) {
      var resourceKey = reservation.getResourceKey();

      assertFalse(repository.existsById(resourceKey));
      assertInstanceOf(EventStore.ReadResult.StreamDoesNotExist.class, eventStore.read(getReservationStreamId(resourceKey)));
    }

    for (var reservation : toKeep) {
      var resourceKey = reservation.getResourceKey();

      assertTrue(repository.existsById(resourceKey));
      var result = eventStore.read(getReservationStreamId(resourceKey));
      assertInstanceOf(EventStore.ReadResult.Success.class, result);
      assertEquals(2, ((EventStore.ReadResult.Success) result).events().length);
    }
  }

  @Autowired
  private ResourceReservationRepository repository;
  private EventStore eventStore;

  private ResourceReservationScavenging resourceReservationScavenging;

  private String reservationStreamId;

  private final ResourceReservation[] toScavenge = new ResourceReservation[]{
    new ResourceReservation(
      getRandomResourceKey(),
      OffsetDateTime.now().minus(Duration.ofDays(1)),
      ResourceReservation.Status.Pending,
      OffsetDateTime.now().minus(Duration.ofDays(1)).minus(Duration.ofMinutes(10)),
      null
    ),
    new ResourceReservation(
      getRandomResourceKey(),
      OffsetDateTime.now().minus(Duration.ofMinutes(1)),
      ResourceReservation.Status.Pending,
      OffsetDateTime.now().minus(Duration.ofMinutes(1)).minus(Duration.ofSeconds(10)),
      OffsetDateTime.now()
    ),
  };

  private final ResourceReservation[] toKeep = new ResourceReservation[]{
    new ResourceReservation(
      getRandomResourceKey(),
      OffsetDateTime.now().minus(Duration.ofDays(2)),
      ResourceReservation.Status.Confirmed,
      OffsetDateTime.now().minus(Duration.ofDays(22)).minus(Duration.ofMinutes(10)),
      null
    ),
    new ResourceReservation(
      getRandomResourceKey(),
      OffsetDateTime.now().minus(Duration.ofMinutes(2)),
      ResourceReservation.Status.Confirmed,
      OffsetDateTime.now().minus(Duration.ofMinutes(2)).minus(Duration.ofSeconds(10)),
      OffsetDateTime.now()
    ),
  };

  @BeforeEach
  void beforeEach() throws ConnectionStringParsingException {
    EventStoreDBClientSettings settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false");
    EventStoreDBClient eventStoreDBClient = EventStoreDBClient.create(settings);
    eventStore = new EventStore(eventStoreDBClient);
    var resourceReservationHandler = new ESDBResourceReservationHandler(Duration.ofMinutes(10), new NulloRetryPolicy(), eventStore);

    resourceReservationScavenging = new ResourceReservationScavenging(repository, resourceReservationHandler);
  }

  private String getRandomResourceKey() {
    var email = "%s@email.com".formatted(UUID.randomUUID().toString().replace("-", ""));
    return Hash.hash(email).toString();
  }

  private String getReservationStreamId(String resourceKey) {
    return "reservation-%s".formatted(resourceKey);
  }
}
