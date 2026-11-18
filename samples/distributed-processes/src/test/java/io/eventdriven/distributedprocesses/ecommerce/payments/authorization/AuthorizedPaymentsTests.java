package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class AuthorizedPaymentsTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final String streamId = PaymentFacade.mapToStreamId(paymentId);
    private final double amount = 62.5;
    private final OffsetDateTime authorizedAt = OffsetDateTime.now();
    private final OffsetDateTime captureBefore = authorizedAt.plus(Duration.ofDays(7));

    private final Database database = new Database();
    private final AuthorizedPayments authorizedPayments = new AuthorizedPayments(database);

    @Test
    public void holdIsExpiredOnceItsDeadlineIsReached() {
        authorize();

        assertThat(authorizedPayments.expiredAt(captureBefore.minusDays(1))).isEmpty();
        assertThat(authorizedPayments.expiredAt(captureBefore)).containsExactly(paymentId);
        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).containsExactly(paymentId);
    }

    @Test
    public void holdWithARequestedCaptureIsNoLongerWatched() {
        authorize();

        authorizedPayments.handle(at(new PaymentCaptureRequested(paymentId, amount, authorizedAt), 2));

        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).isEmpty();
    }

    @Test
    public void voidedHoldIsNoLongerWatched() {
        authorize();

        authorizedPayments.handle(at(new PaymentCanceled(paymentId, authorizedAt), 2));

        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).isEmpty();
    }

    @Test
    public void expiredHoldIsNoLongerWatched() {
        authorize();

        authorizedPayments.handle(at(new PaymentAuthorizationExpired(paymentId, captureBefore), 2));

        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).isEmpty();
    }

    @Test
    public void eventsDeliveredTwiceLeaveASingleHoldWatched() {
        authorize();
        authorize();

        assertThat(authorizedPayments.expiredAt(captureBefore)).containsExactly(paymentId);

        var captureRequested = at(new PaymentCaptureRequested(paymentId, amount, authorizedAt), 2);
        authorizedPayments.handle(captureRequested);
        authorizedPayments.handle(captureRequested);

        assertThat(authorizedPayments.expiredAt(captureBefore)).isEmpty();
    }

    private void authorize() {
        authorizedPayments.handle(at(new PaymentAuthorized(paymentId, authorizedAt, captureBefore), 1));
    }

    private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
        return new EventEnvelope<>(event, new EventMetadata(streamId, streamPosition, streamPosition));
    }
}
