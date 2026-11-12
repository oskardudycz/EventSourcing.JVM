package io.eventdriven.distributedprocesses.ecommerce.payments.capture;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.RecordingPaymentGateway;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static org.assertj.core.api.Assertions.assertThat;

public class CaptureGatewayClientTests {
  private static final Duration paymentTimeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();

  @Test
  public void gatewayIsToldAboutEveryOutcomeTheModuleRecorded() {
    var gateway = new RecordingPaymentGateway(messageBus);
    configure(gateway);

    messageBus.publish(List.of(
      new AuthorizePayment(referenceId, amount),
      new CapturePayment(paymentId),
      new RefundPayment(paymentId)
    ));

    assertThat(gateway.calls).containsExactly(
      "authorize:%s:%s".formatted(paymentId, amount),
      "capture:%s:%s".formatted(paymentId, amount),
      "refund:%s".formatted(paymentId)
    );
  }

  private void configure(PaymentGateway paymentGateway) {
    PaymentsConfig.configure(
      eventStore,
      messageBus,
      paymentGateway,
      paymentTimeout,
      authorizationValidity,
      () -> now
    );
  }
}
