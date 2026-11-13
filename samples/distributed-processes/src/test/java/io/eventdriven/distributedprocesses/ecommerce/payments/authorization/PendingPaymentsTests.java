package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

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

public class PendingPaymentsTests {
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final String streamId = PaymentFacade.mapToStreamId(paymentId);
  private final double amount = 62.5;
  private final OffsetDateTime requestedAt = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = requestedAt.plus(Duration.ofDays(7));

  private final Database database = new Database();
  private final PendingPayments pendingPayments = new PendingPayments(database);

  @Test
  public void requestedAuthorizationWaitsFromTheMomentItWasRequested() {
    request();

    assertThat(pendingPayments.olderThan(requestedAt.plusMinutes(1))).containsExactly(paymentId);
    assertThat(pendingPayments.olderThan(requestedAt)).isEmpty();
  }

  @Test
  public void grantedAuthorizationStopsWaiting() {
    request();

    pendingPayments.handle(at(new PaymentAuthorized(paymentId, requestedAt, expiresAt), 1));

    assertThat(pendingPayments.olderThan(requestedAt.plusMinutes(1))).isEmpty();
  }

  @Test
  public void declinedAuthorizationStopsWaiting() {
    request();

    pendingPayments.handle(at(new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, requestedAt), 1));

    assertThat(pendingPayments.olderThan(requestedAt.plusMinutes(1))).isEmpty();
  }

  @Test
  public void timedOutAuthorizationStopsWaiting() {
    request();

    pendingPayments.handle(at(new PaymentTimedOut(paymentId, requestedAt.plusMinutes(10)), 1));

    assertThat(pendingPayments.olderThan(requestedAt.plusMinutes(20))).isEmpty();
  }

  @Test
  public void eventsDeliveredTwiceLeaveASinglePaymentWaiting() {
    request();
    request();

    assertThat(pendingPayments.olderThan(requestedAt.plusMinutes(1))).containsExactly(paymentId);

    var authorized = at(new PaymentAuthorized(paymentId, requestedAt, expiresAt), 1);
    pendingPayments.handle(authorized);
    pendingPayments.handle(authorized);

    assertThat(pendingPayments.olderThan(requestedAt.plusMinutes(1))).isEmpty();
  }

  private void request() {
    pendingPayments.handle(at(new PaymentAuthorizationRequested(paymentId, referenceId, amount, requestedAt), 0));
  }

  private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
    return new EventEnvelope<>(event, new EventMetadata(streamId, streamPosition, streamPosition));
  }
}
