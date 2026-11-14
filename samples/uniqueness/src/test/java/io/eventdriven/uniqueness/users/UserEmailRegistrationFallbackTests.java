package io.eventdriven.uniqueness.users;

import io.kurrent.dbclient.ConnectionStringParsingException;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBConnectionString;
import io.eventdriven.uniqueness.core.async.SyncProcessor;
import io.eventdriven.uniqueness.core.kurrentdb.EventStore;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.ResourceReservationEventHandler;
import io.eventdriven.uniqueness.core.resourcereservation.kurrentdb.KurrentDBResourceReservationHandler;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservation;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservationRepository;
import io.eventdriven.uniqueness.core.retries.NulloRetryPolicy;
import io.eventdriven.uniqueness.users.reservation.UserEmailReservationEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.uniqueness.core.kurrentdb.subscriptions.KurrentDBSubscription.subscribeToAll;
import static io.eventdriven.uniqueness.core.resourcereservation.kurrentdb.ResourceReservationEvent.*;
import static io.eventdriven.uniqueness.core.serialization.EventSerializer.deserialize;
import static io.eventdriven.uniqueness.users.UserEvent.UserRegistered;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(SpringExtension.class)
public class UserEmailRegistrationFallbackTests {
  @Test
  public void reservationHappyPath_confirmsReservation() throws InterruptedException {
    // Given
    var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
      reservationStreamId,
      new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration)
    );
    var userRegistrationResult = (EventStore.AppendResult.Success) eventStore.append(
      userStreamId,
      new UserRegistered(userId, email, OffsetDateTime.now())
    );
    var reservationConfirmedResult = (EventStore.AppendResult.Success) eventStore.append(
      reservationStreamId,
      reservationResult.nextExpectedRevision(),
      new ResourceReservationConfirmed(resourceKey, OffsetDateTime.now())
    );

    // When
    SyncProcessor.runSync((ack) ->
      subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
        var position = resolvedEvent.getOriginalEvent().getPosition();
        if (position.equals(reservationResult.logPosition()) || position.equals(reservationConfirmedResult.logPosition())) {
          var event = deserialize(resolvedEvent).orElseThrow();

          reservationEventHandler.handle(event);
        }
        if (position.equals(userRegistrationResult.logPosition())) {
          var event = deserialize(resolvedEvent).orElseThrow();

          emailReservationEventHandler.handle(event);
          ack.accept(true);
          subscription.stop();
        }
      })
    );

    // Then
    var resourceReservation = repository.findById(resourceKey).orElse(null);

    assertNotNull(resourceReservation);
    assertEquals(resourceKey, resourceReservation.getResourceKey());
    assertEquals(ResourceReservation.Status.Confirmed, resourceReservation.getStatus());
    assertNotNull(resourceReservation.getReservedAt());
  }

  @Test
  public void reservationInitiated_storesPendingLookup() throws InterruptedException {
    // Given
    var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
      reservationStreamId,
      new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration)
    );

    // When
    SyncProcessor.runSync((ack) ->
      subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
        var position = resolvedEvent.getOriginalEvent().getPosition();
        if (position.equals(reservationResult.logPosition())) {
          var event = deserialize(resolvedEvent).orElseThrow();

          reservationEventHandler.handle(event);
        }
      })
    );

    // Then
    var resourceReservation = repository.findById(resourceKey).orElse(null);

    assertNotNull(resourceReservation);
    assertEquals(resourceKey, resourceReservation.getResourceKey());
    assertEquals(ResourceReservation.Status.Pending, resourceReservation.getStatus());
  }

  @Test
  public void emailReservationInitiatedAndUserRegistered_eventuallyConfirmsReservation() throws InterruptedException {
    // Given
    var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
      reservationStreamId,
      new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration)
    );
    var userRegistrationResult = (EventStore.AppendResult.Success) eventStore.append(
      userStreamId,
      new UserRegistered(userId, email, OffsetDateTime.now())
    );

    // When
    SyncProcessor.runSync((ack) ->
      subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
        var position = resolvedEvent.getOriginalEvent().getPosition();
        if (position.equals(reservationResult.logPosition())) {
          var event = deserialize(resolvedEvent).orElseThrow();

          reservationEventHandler.handle(event);
        }
        if (position.equals(userRegistrationResult.logPosition())) {
          var event = deserialize(resolvedEvent).orElseThrow();

          emailReservationEventHandler.handle(event);
          ack.accept(true);
          subscription.stop();
        }
      })
    );

    // Then
    var resourceReservation = repository.findById(resourceKey).orElse(null);

    assertNotNull(resourceReservation);
    assertEquals(resourceKey, resourceReservation.getResourceKey());
    assertEquals(ResourceReservation.Status.Confirmed, resourceReservation.getStatus());
    assertNotNull(resourceReservation.getReservedAt());
  }


  @Test
  public void releasedReservation_removesLookup() throws InterruptedException {
    // Given
    var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
      reservationStreamId,
      new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration)
    );
    var reservationReleaseInitiatedResult = (EventStore.AppendResult.Success) eventStore.append(
      reservationStreamId,
      reservationResult.nextExpectedRevision(),
      new ResourceReservationReleaseInitiated(resourceKey, OffsetDateTime.now())
    );

    // When
    SyncProcessor.runSync((ack) ->
      subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
        var position = resolvedEvent.getOriginalEvent().getPosition();
        if (position.equals(reservationResult.logPosition()) || position.equals(reservationReleaseInitiatedResult.logPosition())) {
          var event = deserialize(resolvedEvent).orElseThrow();

          reservationEventHandler.handle(event);
        }
      })
    );

    // Then
    var resourceReservation = repository.findById(resourceKey).orElse(null);

    assertNull(resourceReservation);
  }

  @Autowired
  private ResourceReservationRepository repository;
  private KurrentDBClient kurrentDBClient;
  private EventStore eventStore;
  private ResourceReservationEventHandler reservationEventHandler;
  private UserEmailReservationEventHandler emailReservationEventHandler;

  private final Duration reservationLockDuration = Duration.ofMinutes(15);
  private UUID userId;
  private String email;
  private String resourceKey;
  private String userStreamId;
  private String reservationStreamId;

  @BeforeEach
  void beforeEach() throws ConnectionStringParsingException {
    KurrentDBClientSettings settings = KurrentDBConnectionString.parseOrThrow("kurrentdb://localhost:2113?tls=false");
    kurrentDBClient = KurrentDBClient.create(settings);
    eventStore = new EventStore(kurrentDBClient);
    var resourceReservationCommandHandler = new KurrentDBResourceReservationHandler(reservationLockDuration, new NulloRetryPolicy(), eventStore);

    reservationEventHandler = new ResourceReservationEventHandler(repository, resourceReservationCommandHandler);
    emailReservationEventHandler = new UserEmailReservationEventHandler(repository);

    userId = UUID.randomUUID();
    email = "%s@email.com".formatted(UUID.randomUUID().toString().replace("-", ""));
    userStreamId = "user-%s".formatted(userId);

    resourceKey = Hash.hash(email).toString();
    reservationStreamId = "reservation-%s".formatted(resourceKey);
  }
}
