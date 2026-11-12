package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizedPaymentsTests {
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final String streamId = PaymentFacade.mapToStreamId(paymentId);
  private final double amount = 62.5;
  private final OffsetDateTime authorizedAt = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = authorizedAt.plus(Duration.ofDays(7));

  private final Database database = new Database();
  private final AuthorizedPayments authorizedPayments = new AuthorizedPayments(database);

  @Test
  public void holdIsExpiredOnceItsDeadlineIsReached() {
    authorize();

    assertThat(authorizedPayments.expiredAt(expiresAt.minusDays(1))).isEmpty();
    assertThat(authorizedPayments.expiredAt(expiresAt)).containsExactly(paymentId);
    assertThat(authorizedPayments.expiredAt(expiresAt.plusDays(1))).containsExactly(paymentId);
  }

  @Test
  public void capturedHoldIsNoLongerWatched() {
    authorize();

    authorizedPayments.onCaptured(at(new PaymentCaptured(paymentId, amount, authorizedAt), 2));

    assertThat(authorizedPayments.expiredAt(expiresAt.plusDays(1))).isEmpty();
  }

  @Test
  public void voidedHoldIsNoLongerWatched() {
    authorize();

    authorizedPayments.onVoided(at(new PaymentVoided(paymentId, authorizedAt), 2));

    assertThat(authorizedPayments.expiredAt(expiresAt.plusDays(1))).isEmpty();
  }

  @Test
  public void expiredHoldIsNoLongerWatched() {
    authorize();

    authorizedPayments.onAuthorizationExpired(at(new PaymentAuthorizationExpired(paymentId, expiresAt), 2));

    assertThat(authorizedPayments.expiredAt(expiresAt.plusDays(1))).isEmpty();
  }

  @Test
  public void eventsDeliveredTwiceLeaveASingleHoldWatched() {
    authorize();
    authorize();

    assertThat(authorizedPayments.expiredAt(expiresAt)).containsExactly(paymentId);

    var captured = at(new PaymentCaptured(paymentId, amount, authorizedAt), 2);
    authorizedPayments.onCaptured(captured);
    authorizedPayments.onCaptured(captured);

    assertThat(authorizedPayments.expiredAt(expiresAt)).isEmpty();
  }

  private void authorize() {
    authorizedPayments.onAuthorized(at(new PaymentAuthorized(paymentId, authorizedAt, expiresAt), 1));
  }

  private <Event> EventEnvelope<Event> at(Event event, long streamPosition) {
    return new EventEnvelope<>(event, new EventMetadata(streamId, streamPosition, streamPosition));
  }
}
