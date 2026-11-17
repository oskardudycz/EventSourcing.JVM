package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.CheckoutSession;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentValidity;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.testing.MessageCatcher;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PaymentExternalEventForwarderOrderingTests {
  private static final Duration paymentTimeout = Duration.ofMinutes(5);
  private static final Duration authorizationValidity = Duration.ofDays(7);

  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final PaymentId paymentId = PaymentId.derivedFrom(referenceId);
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime captureBefore = now.plus(authorizationValidity);

  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final InMemoryMessageBus messageBus = new InMemoryMessageBus();
  private final MessageCatcher externalEvents = new MessageCatcher();
  private final List<String> transcript = new ArrayList<>();

  @Test
  public void authorizationConfirmedByTheWebhookIsForwardedWithTheReference() {
    configure(new TranscribingPaymentGateway());
    messageBus.publish(List.of(new AuthorizePayment(paymentId, referenceId, amount)));

    messageBus.publish(List.of(new ConfirmPaymentAuthorization(paymentId, captureBefore)));

    externalEvents.shouldReceiveMessages(
      new PaymentAuthorized(referenceId, paymentId, now)
    );
  }

  @Test
  public void captureIsPublishedOnlyAfterTheGatewayConfirmedIt() {
    configure(new TranscribingPaymentGateway());
    messageBus.publish(List.of(
      new AuthorizePayment(paymentId, referenceId, amount),
      new ConfirmPaymentAuthorization(paymentId, captureBefore)
    ));
    transcript.clear();

    messageBus.publish(List.of(new CapturePayment(paymentId)));

    assertThat(transcript).containsExactly("capture", "published:PaymentCaptured");
  }

  private void configure(PaymentGateway paymentGateway) {
    messageBus.use(message -> {
      if (message instanceof PaymentExternalEvent) {
        externalEvents.catchMessage(message);
        transcript.add("published:" + message.getClass().getSimpleName());
      }
    });

    PaymentsConfig.configure(
      eventStore,
      messageBus,
      paymentGateway,
      PaymentValidity.of(paymentTimeout),
      () -> now
    );
  }

  // Grants the capture straight away and writes down every call in the shared transcript.
  private class TranscribingPaymentGateway implements PaymentGateway {
    @Override
    public CheckoutSession createCheckoutSession(
      PaymentId paymentId,
      double amount,
      URI successUrl,
      URI cancelUrl,
      OffsetDateTime expiresAt
    ) {
      transcript.add("checkout-session");
      return new CheckoutSession(URI.create("https://checkout.example/c/pay/" + paymentId.tail()), expiresAt);
    }

    @Override
    public void expireCheckoutSession(PaymentId paymentId) {
      transcript.add("expire-checkout-session");
    }

    @Override
    public void capture(PaymentId paymentId, double amount) {
      transcript.add("capture");
      messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
    }

    @Override
    public void cancel(PaymentId paymentId) {
      transcript.add("cancel");
    }
  }
}
