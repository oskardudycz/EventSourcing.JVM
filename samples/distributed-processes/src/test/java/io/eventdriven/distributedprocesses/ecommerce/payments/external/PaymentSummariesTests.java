package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PaymentSummariesTests {
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final String streamId = PaymentFacade.mapToStreamId(paymentId);
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = now.plus(Duration.ofDays(7));

  private final Database database = new Database();
  private final PaymentSummaries summaries = new PaymentSummaries(database);

  @Test
  public void requestedAuthorizationIsSummarizedAsPending() {
    summaries.onAuthorizationRequested(at(new PaymentAuthorizationRequested(paymentId, referenceId, amount), 0));

    var summary = summary();

    assertThat(summary.getPaymentId()).isEqualTo(paymentId);
    assertThat(summary.getReferenceId()).isEqualTo(referenceId);
    assertThat(summary.getAmount()).isEqualTo(amount);
    assertThat(summary.getStatus()).isEqualTo(PaymentSummary.Status.Pending);
    assertThat(summary.getAuthorizedAt()).isNull();
    assertThat(summary.getExpiresAt()).isNull();
  }

  @Test
  public void grantedAuthorizationRecordsWhenItWasGivenAndWhenItRunsOut() {
    request();

    summaries.onAuthorized(at(new PaymentAuthorized(paymentId, now, expiresAt), 1));

    var summary = summary();

    assertThat(summary.getStatus()).isEqualTo(PaymentSummary.Status.Authorized);
    assertThat(summary.getAuthorizedAt()).isEqualTo(now);
    assertThat(summary.getExpiresAt()).isEqualTo(expiresAt);
    assertThat(summary.getReferenceId()).isEqualTo(referenceId);
    assertThat(summary.getAmount()).isEqualTo(amount);
  }

  @Test
  public void captureVoidAndRefundAreMirroredAsStatuses() {
    authorize();

    summaries.onCaptured(at(new PaymentCaptured(paymentId, amount, now), 2));
    assertThat(summary().getStatus()).isEqualTo(PaymentSummary.Status.Captured);

    summaries.onRefunded(at(new PaymentRefunded(paymentId, now), 3));
    assertThat(summary().getStatus()).isEqualTo(PaymentSummary.Status.Refunded);
  }

  @Test
  public void voidedAuthorizationIsMirroredAsVoided() {
    authorize();

    summaries.onVoided(at(new PaymentVoided(paymentId, now), 2));

    assertThat(summary().getStatus()).isEqualTo(PaymentSummary.Status.Voided);
  }

  @Test
  public void everyUnhappyOutcomeIsSummarizedAsFailed() {
    request();
    summaries.onDeclined(at(new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, now), 1));
    assertThat(summary().getStatus()).isEqualTo(PaymentSummary.Status.Failed);

    var timedOut = new PaymentSummaries(new Database());
    timedOut.onAuthorizationRequested(at(new PaymentAuthorizationRequested(paymentId, referenceId, amount), 0));
    timedOut.onTimedOut(at(new PaymentTimedOut(paymentId, now), 1));
    assertThat(timedOut.get(paymentId).orElseThrow().getStatus())
      .isEqualTo(PaymentSummary.Status.Failed);

    var expired = new PaymentSummaries(new Database());
    expired.onAuthorizationRequested(at(new PaymentAuthorizationRequested(paymentId, referenceId, amount), 0));
    expired.onAuthorized(at(new PaymentAuthorized(paymentId, now, expiresAt), 1));
    expired.onAuthorizationExpired(at(new PaymentAuthorizationExpired(paymentId, expiresAt), 2));
    assertThat(expired.get(paymentId).orElseThrow().getStatus())
      .isEqualTo(PaymentSummary.Status.Failed);
  }

  @Test
  public void anEventDeliveredTwiceLeavesTheSummaryWhereItAlreadyIs() {
    authorize();
    summaries.onCaptured(at(new PaymentCaptured(paymentId, amount, now), 2));

    summaries.onAuthorized(at(new PaymentAuthorized(paymentId, now, expiresAt), 1));

    var summary = summary();

    assertThat(summary.getStatus()).isEqualTo(PaymentSummary.Status.Captured);
    assertThat(summary.getVersion()).isEqualTo(2);
  }

  @Test
  public void summaryOfAPaymentThatWasNeverRequestedIsNotThere() {
    assertThat(summaries.get(paymentId)).isEmpty();
  }

  private void request() {
    summaries.onAuthorizationRequested(at(new PaymentAuthorizationRequested(paymentId, referenceId, amount), 0));
  }

  private void authorize() {
    request();
    summaries.onAuthorized(at(new PaymentAuthorized(paymentId, now, expiresAt), 1));
  }

  private PaymentSummary summary() {
    return summaries.get(paymentId).orElseThrow();
  }

  private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
    return new EventEnvelope<>(event, new EventMetadata(streamId, streamPosition));
  }
}
