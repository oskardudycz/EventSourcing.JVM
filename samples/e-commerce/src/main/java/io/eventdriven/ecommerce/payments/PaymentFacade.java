package io.eventdriven.ecommerce.payments;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.PaymentDecider.*;

import io.eventdriven.core.commands.CommandHandler;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class PaymentFacade {
    private static final Duration checkoutSessionLifetime = Duration.ofMinutes(30);

    private final CommandHandler<Payment.Id, Payment, PaymentEvent> commandHandler;
    private final Payment.Validity paymentValidity;
    private final PaymentGateway paymentGateway;
    private final Supplier<OffsetDateTime> now;

    public static String mapToStreamId(Payment.Id paymentId) {
        return "Payment-%s".formatted(paymentId.tail());
    }

    public PaymentFacade(
            CommandHandler<Payment.Id, Payment, PaymentEvent> commandHandler,
            Payment.Validity paymentValidity,
            PaymentGateway paymentGateway,
            Supplier<OffsetDateTime> now) {
        this.commandHandler = commandHandler;
        this.paymentValidity = paymentValidity;
        this.paymentGateway = paymentGateway;
        this.now = now;
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.requests,
                            description =
                                    "Records a payment that waits for the customer to authorize it on the payment provider's page."))
    public void authorizePayment(AuthorizePayment command) {
        var requestedAt = now.get();
        var decided = new AuthorizePayment(
                command.paymentId(),
                command.referenceId(),
                command.amount(),
                requestedAt,
                paymentValidity.timesOutAt(requestedAt));

        commandHandler.handle(command.paymentId(), decided, PaymentDecider::requestAuthorization);
    }

    public PaymentGateway.CheckoutSession createCheckoutSession(CreateCheckoutSession command) {
        var createdAt = now.get();
        var payment = commandHandler.get(command.paymentId());

        if (!payment.exists() || !payment.referenceId().equals(command.referenceId()))
            throw new NoSuchElementException("Payment '%s' for '%s' was not found"
                    .formatted(command.paymentId().value(), command.referenceId()));

        if (!payment.isPending())
            throw new IllegalStateException(
                    "Payment '%s' is not pending".formatted(command.paymentId().value()));

        var openSession = payment.openCheckoutSession(createdAt);
        if (openSession.isPresent()) return openSession.get();

        PaymentGateway.CheckoutSession session;
        try {
            session = paymentGateway.createCheckoutSession(
                    command.paymentId(),
                    payment.amount(),
                    command.successUrl(),
                    command.cancelUrl(),
                    createdAt.plus(checkoutSessionLifetime));
        } catch (RuntimeException failedCall) {
            throw new PaymentGateway.GatewayException(
                    "Opening a checkout session for payment '%s' failed"
                            .formatted(command.paymentId().value()),
                    failedCall);
        }

        commandHandler.handle(
                command.paymentId(),
                new RecordCheckoutSession(command.paymentId(), session, createdAt),
                PaymentDecider::recordCheckoutSession);

        return session;
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description =
                                    "Records that the payment is authorized, and the date by which the shop must charge the customer."))
    public void confirmPaymentAuthorization(ConfirmPaymentAuthorization command) {
        var decided = new ConfirmPaymentAuthorization(command.paymentId(), command.captureBefore(), now.get());

        commandHandler.handleContinuingTrace(command.paymentId(), decided, PaymentDecider::confirmAuthorization);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.requests,
                            description = "Asks the payment provider to charge the customer."))
    public void capturePayment(CapturePayment command) {
        var decided = new CapturePayment(command.paymentId(), now.get());

        commandHandler.handle(command.paymentId(), decided, PaymentDecider::requestCapture);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description = "Records that the customer was charged."))
    public void confirmPaymentCapture(ConfirmPaymentCapture command) {
        var decided = new ConfirmPaymentCapture(command.paymentId(), now.get());

        commandHandler.handleContinuingTrace(command.paymentId(), decided, PaymentDecider::confirmCapture);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description = "Records that charging the customer failed."))
    public void failPaymentCapture(FailPaymentCapture command) {
        var decided = new FailPaymentCapture(command.paymentId(), command.reason(), now.get());

        commandHandler.handleContinuingTrace(command.paymentId(), decided, PaymentDecider::failCapture);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.requests,
                            description = "Cancels the authorized payment, so the customer is never charged."))
    public void cancelPayment(CancelPayment command) {
        var decided = new CancelPayment(command.paymentId(), now.get());

        commandHandler.handle(command.paymentId(), decided, PaymentDecider::cancel);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description = "Fails the payment, because the customer did not pay in time."))
    public void timeOutPayment(TimeOutPayment command) {
        commandHandler.handleContinuingTrace(command.paymentId(), command, PaymentDecider::timeOut);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description =
                                    "Fails the payment, because its authorization ran out before the shop charged the customer."))
    public void expirePaymentAuthorization(ExpirePaymentAuthorization command) {
        commandHandler.handleContinuingTrace(command.paymentId(), command, PaymentDecider::expireAuthorization);
    }

    @Schema(description = "Opens the payment provider's page, where the customer pays.")
    public record CreateCheckoutSession(Payment.Id paymentId, String referenceId, URI successUrl, URI cancelUrl) {}
}
