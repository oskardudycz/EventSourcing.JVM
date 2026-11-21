package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.testing.Metadata;
import io.eventdriven.testing.ProjectionsDatabase;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class AuthorizedPaymentsTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final String streamId = PaymentFacade.mapToStreamId(paymentId);
    private final double amount = 62.5;
    private final OffsetDateTime authorizedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final OffsetDateTime captureBefore = authorizedAt.plus(Duration.ofDays(7));

    private final AuthorizedPayments authorizedPayments;

    AuthorizedPaymentsTests(ProjectionsDatabase database) {
        authorizedPayments = new AuthorizedPayments(database.bean(AuthorizedPaymentRepository.class));
    }

    @Test
    public void holdIsExpiredOnceItsDeadlineIsReached() {
        authorize();

        assertThat(authorizedPayments.expiredAt(captureBefore.minusDays(1))).doesNotContain(paymentId);
        assertThat(authorizedPayments.expiredAt(captureBefore)).containsOnlyOnce(paymentId);
        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).containsOnlyOnce(paymentId);
    }

    @Test
    public void holdWithARequestedCaptureIsNoLongerWatched() {
        authorize();

        authorizedPayments.handle(at(new PaymentCaptureRequested(paymentId, amount, authorizedAt), 2));

        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).doesNotContain(paymentId);
    }

    @Test
    public void voidedHoldIsNoLongerWatched() {
        authorize();

        authorizedPayments.handle(at(new PaymentCanceled(paymentId, authorizedAt), 2));

        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).doesNotContain(paymentId);
    }

    @Test
    public void expiredHoldIsNoLongerWatched() {
        authorize();

        authorizedPayments.handle(at(new PaymentAuthorizationExpired(paymentId, captureBefore), 2));

        assertThat(authorizedPayments.expiredAt(captureBefore.plusDays(1))).doesNotContain(paymentId);
    }

    @Test
    public void eventsDeliveredTwiceLeaveASingleHoldWatched() {
        authorize();
        authorize();

        assertThat(authorizedPayments.expiredAt(captureBefore)).containsOnlyOnce(paymentId);

        var captureRequested = at(new PaymentCaptureRequested(paymentId, amount, authorizedAt), 2);
        authorizedPayments.handle(captureRequested);
        authorizedPayments.handle(captureRequested);

        assertThat(authorizedPayments.expiredAt(captureBefore)).doesNotContain(paymentId);
    }

    private void authorize() {
        authorizedPayments.handle(at(new PaymentAuthorized(paymentId, authorizedAt, captureBefore), 1));
    }

    private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
        return new EventEnvelope<>(event, Metadata.at(streamId, streamPosition));
    }
}
