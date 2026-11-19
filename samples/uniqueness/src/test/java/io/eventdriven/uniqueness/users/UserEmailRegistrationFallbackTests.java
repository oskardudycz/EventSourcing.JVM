package io.eventdriven.uniqueness.users;

import static io.eventdriven.uniqueness.core.kurrentdb.subscriptions.KurrentDBSubscription.subscribeToAll;
import static io.eventdriven.uniqueness.core.resourcereservation.kurrentdb.ResourceReservationEvent.*;
import static io.eventdriven.uniqueness.core.serialization.EventSerializer.deserialize;
import static io.eventdriven.uniqueness.users.UserEvent.UserRegistered;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.uniqueness.core.async.SyncProcessor;
import io.eventdriven.uniqueness.core.kurrentdb.EventStore;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.ResourceReservationEventHandler;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservation;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservationRepository;
import io.eventdriven.uniqueness.core.resourcereservation.kurrentdb.KurrentDBResourceReservationHandler;
import io.eventdriven.uniqueness.testing.TestDatabases;
import io.eventdriven.uniqueness.users.reservation.UserEmailReservationEventHandler;
import io.kurrent.dbclient.ConnectionStringParsingException;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBConnectionString;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith(SpringExtension.class)
public class UserEmailRegistrationFallbackTests {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabases.postgreSQL().connectionString());
        registry.add(
                "spring.datasource.username", () -> TestDatabases.postgreSQL().userName());
        registry.add(
                "spring.datasource.password", () -> TestDatabases.postgreSQL().password());
    }

    @Test
    public void reservationHappyPath_confirmsReservation() throws InterruptedException {
        // Given
        var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
                reservationStreamId,
                new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration));
        var userRegistrationResult = (EventStore.AppendResult.Success)
                eventStore.append(userStreamId, new UserRegistered(userId, email, OffsetDateTime.now()));
        var reservationConfirmedResult = (EventStore.AppendResult.Success) eventStore.append(
                reservationStreamId,
                reservationResult.nextExpectedRevision(),
                new ResourceReservationConfirmed(resourceKey, OffsetDateTime.now()));

        // When
        SyncProcessor.runSync((ack) -> subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
            var position = resolvedEvent.getOriginalEvent().getPosition();
            if (position.equals(reservationResult.logPosition())
                    || position.equals(reservationConfirmedResult.logPosition())) {
                var event = deserialize(resolvedEvent).orElseThrow();

                reservationEventHandler.handle(event);
            }
            if (position.equals(userRegistrationResult.logPosition())) {
                var event = deserialize(resolvedEvent).orElseThrow();

                emailReservationEventHandler.handle(event);
                ack.accept(true);
                subscription.stop();
            }
        }));

        // Then
        var resourceReservation = repository.findById(resourceKey).orElse(null);

        assertThat(resourceReservation).isNotNull();
        assertThat(resourceReservation.getResourceKey()).isEqualTo(resourceKey);
        assertThat(resourceReservation.getStatus()).isEqualTo(ResourceReservation.Status.Confirmed);
        assertThat(resourceReservation.getReservedAt()).isNotNull();
    }

    @Test
    public void reservationInitiated_storesPendingLookup() throws InterruptedException {
        // Given
        var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
                reservationStreamId,
                new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration));

        // When
        SyncProcessor.runSync((ack) -> subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
            var position = resolvedEvent.getOriginalEvent().getPosition();
            if (position.equals(reservationResult.logPosition())) {
                var event = deserialize(resolvedEvent).orElseThrow();

                reservationEventHandler.handle(event);
            }
        }));

        // Then
        var resourceReservation = repository.findById(resourceKey).orElse(null);

        assertThat(resourceReservation).isNotNull();
        assertThat(resourceReservation.getResourceKey()).isEqualTo(resourceKey);
        assertThat(resourceReservation.getStatus()).isEqualTo(ResourceReservation.Status.Pending);
    }

    @Test
    public void emailReservationInitiatedAndUserRegistered_eventuallyConfirmsReservation() throws InterruptedException {
        // Given
        var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
                reservationStreamId,
                new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration));
        var userRegistrationResult = (EventStore.AppendResult.Success)
                eventStore.append(userStreamId, new UserRegistered(userId, email, OffsetDateTime.now()));

        // When
        SyncProcessor.runSync((ack) -> subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
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
        }));

        // Then
        var resourceReservation = repository.findById(resourceKey).orElse(null);

        assertThat(resourceReservation).isNotNull();
        assertThat(resourceReservation.getResourceKey()).isEqualTo(resourceKey);
        assertThat(resourceReservation.getStatus()).isEqualTo(ResourceReservation.Status.Confirmed);
        assertThat(resourceReservation.getReservedAt()).isNotNull();
    }

    @Test
    public void releasedReservation_removesLookup() throws InterruptedException {
        // Given
        var reservationResult = (EventStore.AppendResult.Success) eventStore.append(
                reservationStreamId,
                new ResourceReservationInitiated(resourceKey, OffsetDateTime.now(), reservationLockDuration));
        var reservationReleaseInitiatedResult = (EventStore.AppendResult.Success) eventStore.append(
                reservationStreamId,
                reservationResult.nextExpectedRevision(),
                new ResourceReservationReleaseInitiated(resourceKey, OffsetDateTime.now()));

        // When
        SyncProcessor.runSync((ack) -> subscribeToAll(kurrentDBClient, (subscription, resolvedEvent) -> {
            var position = resolvedEvent.getOriginalEvent().getPosition();
            if (position.equals(reservationResult.logPosition())
                    || position.equals(reservationReleaseInitiatedResult.logPosition())) {
                var event = deserialize(resolvedEvent).orElseThrow();

                reservationEventHandler.handle(event);
            }
        }));

        // Then
        var resourceReservation = repository.findById(resourceKey).orElse(null);

        assertThat(resourceReservation).isNull();
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
        KurrentDBClientSettings settings =
                KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString());
        kurrentDBClient = KurrentDBClient.create(settings);
        eventStore = new EventStore(kurrentDBClient);
        var resourceReservationCommandHandler = new KurrentDBResourceReservationHandler(
                reservationLockDuration, new RetryTemplate(RetryPolicy.withMaxRetries(0)), eventStore);

        reservationEventHandler = new ResourceReservationEventHandler(repository, resourceReservationCommandHandler);
        emailReservationEventHandler = new UserEmailReservationEventHandler(repository);

        userId = UUID.randomUUID();
        email = "%s@email.com".formatted(UUID.randomUUID().toString().replace("-", ""));
        userStreamId = "user-%s".formatted(userId);

        resourceKey = Hash.hash(email).toString();
        reservationStreamId = "reservation-%s".formatted(resourceKey);
    }
}
