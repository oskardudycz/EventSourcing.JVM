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
public class PendingPaymentsTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
    private final String streamId = PaymentFacade.mapToStreamId(paymentId);
    private final double amount = 62.5;
    private final OffsetDateTime requestedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final OffsetDateTime timesOutAt = requestedAt.plusMinutes(15);
    private final OffsetDateTime captureBefore = requestedAt.plus(Duration.ofDays(7));

    private final PendingPayments pendingPayments;

    PendingPaymentsTests(ProjectionsDatabase database) {
        pendingPayments = new PendingPayments(database.bean(PendingPaymentRepository.class));
    }

    @Test
    public void requestedAuthorizationTimesOutAtTheDeadlineThePaymentGave() {
        request();

        assertThat(pendingPayments.timedOutAt(timesOutAt)).containsOnlyOnce(paymentId);
        assertThat(pendingPayments.timedOutAt(timesOutAt.minusMinutes(1))).doesNotContain(paymentId);
    }

    @Test
    public void grantedAuthorizationStopsWaiting() {
        request();

        pendingPayments.handle(at(new PaymentAuthorized(paymentId, requestedAt, captureBefore), 1));

        assertThat(pendingPayments.timedOutAt(timesOutAt)).doesNotContain(paymentId);
    }

    @Test
    public void timedOutAuthorizationStopsWaiting() {
        request();

        pendingPayments.handle(at(new PaymentTimedOut(paymentId, requestedAt.plusMinutes(10)), 1));

        assertThat(pendingPayments.timedOutAt(timesOutAt.plusMinutes(20))).doesNotContain(paymentId);
    }

    @Test
    public void eventsDeliveredTwiceLeaveASinglePaymentWaiting() {
        request();
        request();

        assertThat(pendingPayments.timedOutAt(timesOutAt)).containsOnlyOnce(paymentId);

        var authorized = at(new PaymentAuthorized(paymentId, requestedAt, captureBefore), 1);
        pendingPayments.handle(authorized);
        pendingPayments.handle(authorized);

        assertThat(pendingPayments.timedOutAt(timesOutAt)).doesNotContain(paymentId);
    }

    private void request() {
        pendingPayments.handle(
                at(new PaymentAuthorizationRequested(paymentId, referenceId, amount, requestedAt, timesOutAt), 0));
    }

    private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
        return new EventEnvelope<>(event, Metadata.at(streamId, streamPosition));
    }
}
