package io.eventdriven.uniqueness.users;

import static io.eventdriven.uniqueness.core.kurrentdb.EventStore.ReadResult;
import static io.eventdriven.uniqueness.core.resourcereservation.kurrentdb.ResourceReservationEvent.*;
import static io.eventdriven.uniqueness.core.serialization.EventSerializer.deserialize;
import static io.eventdriven.uniqueness.users.UserEvent.UserRegistered;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.uniqueness.core.kurrentdb.EventStore;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.kurrentdb.KurrentDBResourceReservationHandler;
import io.eventdriven.uniqueness.testing.TestDatabases;
import io.eventdriven.uniqueness.users.reservation.UserCommandHandler;
import io.kurrent.dbclient.ConnectionStringParsingException;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBConnectionString;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

public class UserEmailRegistrationTests {
    @Test
    public void forNotUsedEmail_ReservationSucceeds() {
        var userId = UUID.randomUUID();
        var email = "%s@email.com".formatted(UUID.randomUUID().toString().replace("-", ""));

        assertThat(userCommandHandler.registerUser(userId, email)).isTrue();

        var userReadResult = eventStore.read("user-%s".formatted(userId));

        assertThat(userReadResult).isInstanceOf(ReadResult.Success.class);

        if (userReadResult instanceof ReadResult.Success success) {
            var userEvents = success.events();
            assertThat(userEvents.length).isEqualTo(1);

            var userRegistered = deserialize(UserRegistered.class, userEvents[0]);

            assertThat(userRegistered.isPresent()).isTrue();

            assertThat(userRegistered.get().userId()).isEqualTo(userId);
            assertThat(userRegistered.get().email()).isEqualTo(email);
        }

        var resourceKey = Hash.hash(email).toString();
        var reservationReadResult = eventStore.read("reservation-%s".formatted(resourceKey));

        assertThat(reservationReadResult).isInstanceOf(ReadResult.Success.class);

        if (reservationReadResult instanceof ReadResult.Success success) {
            var reservationEvents = success.events();
            assertThat(reservationEvents.length).isEqualTo(2);

            var reservationInitiated = deserialize(ResourceReservationInitiated.class, reservationEvents[0]);

            assertThat(reservationInitiated.isPresent()).isTrue();

            assertThat(reservationInitiated.get().resourceKey()).isEqualTo(resourceKey);
            assertThat(reservationInitiated.get().tentativeLockFor()).isEqualTo(reservationLockDuration);

            var reservationConfirmed = deserialize(ResourceReservationConfirmed.class, reservationEvents[1]);

            assertThat(reservationConfirmed.isPresent()).isTrue();

            assertThat(reservationConfirmed.get().resourceKey()).isEqualTo(resourceKey);
            assertThat(reservationConfirmed.get().reservedAt()).isNotNull();
        }
    }

    private final Duration reservationLockDuration = Duration.ofMinutes(15);
    private EventStore eventStore;
    private UserCommandHandler userCommandHandler;

    @BeforeEach
    void beforeEach() throws ConnectionStringParsingException {
        KurrentDBClientSettings settings =
                KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString());
        var kurrentDBClient = KurrentDBClient.create(settings);
        eventStore = new EventStore(kurrentDBClient);

        var resourceReservationCommandHandler = new KurrentDBResourceReservationHandler(
                reservationLockDuration, new RetryTemplate(RetryPolicy.withMaxRetries(0)), eventStore);
        userCommandHandler = new UserCommandHandler(resourceReservationCommandHandler, eventStore);
    }
}
