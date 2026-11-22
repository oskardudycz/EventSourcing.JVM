package io.eventdriven.ecommerce.payments.external;

import static io.eventdriven.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentFacade;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.projections.ProjectionsDatabase;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

@ParameterizedClass
@EnumSource(ProjectionsDatabase.class)
public class PaymentSummariesTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);
    private final String streamId = PaymentFacade.mapToStreamId(paymentId);
    private final double amount = 62.5;
    private final OffsetDateTime now = OffsetDateTime.now();

    private final PaymentSummaries summaries;

    PaymentSummariesTests(ProjectionsDatabase database) {
        summaries = new PaymentSummaries(database.bean(PaymentSummaryRepository.class));
    }

    @Test
    public void requestedAuthorizationIsSummarizedWithTheReference() {
        request();

        var summary = summary();

        assertThat(summary.getReferenceId()).isEqualTo(referenceId);
        assertThat(summary.getVersion()).isEqualTo(0);
    }

    @Test
    public void onlyTheRequestIsSummarizedBecauseNothingLaterChangesTheReference() {
        assertThat(summaries.canHandle()).containsExactly(PaymentAuthorizationRequested.class);
    }

    @Test
    public void summaryOfAPaymentThatWasNeverRequestedIsNotThere() {
        assertThat(summaries.get(paymentId)).isEmpty();
    }

    private void request() {
        summaries.handle(
                at(0, new PaymentAuthorizationRequested(paymentId, referenceId, amount, now, now.plusMinutes(15))));
    }

    private PaymentSummary summary() {
        return summaries.get(paymentId).orElseThrow();
    }

    private <Event> EventEnvelope<Event> at(long streamPosition, Event event) {
        return new EventEnvelope<>(event, Metadata.at(streamId, streamPosition));
    }
}
