package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.*;

import io.eventdriven.distributedprocesses.core.commands.CommandHandler;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class PaymentFacade {
    private static final Duration checkoutSessionLifetime = Duration.ofMinutes(30);

    private final CommandHandler<PaymentId, Payment, PaymentCommand, PaymentEvent> commandHandler;
    private final PaymentGateway paymentGateway;
    private final Supplier<OffsetDateTime> now;

    public static String mapToStreamId(PaymentId paymentId) {
        return "Payment-%s".formatted(paymentId.tail());
    }

    public PaymentFacade(
            CommandHandler<PaymentId, Payment, PaymentCommand, PaymentEvent> commandHandler,
            PaymentGateway paymentGateway,
            Supplier<OffsetDateTime> now) {
        this.commandHandler = commandHandler;
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
        commandHandler.handle(command.paymentId(), command);
    }

    public CheckoutSession createCheckoutSession(CreateCheckoutSession command) {
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

        CheckoutSession session;
        try {
            session = paymentGateway.createCheckoutSession(
                    command.paymentId(),
                    payment.amount(),
                    command.successUrl(),
                    command.cancelUrl(),
                    createdAt.plus(checkoutSessionLifetime));
        } catch (RuntimeException failedCall) {
            throw new PaymentGatewayException(
                    "Opening a checkout session for payment '%s' failed"
                            .formatted(command.paymentId().value()),
                    failedCall);
        }

        commandHandler.handle(command.paymentId(), new RecordCheckoutSession(command.paymentId(), session));

        return session;
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description =
                                    "Records that the payment is authorized, and the date by which the shop must charge the customer."))
    public void confirmPaymentAuthorization(ConfirmPaymentAuthorization command) {
        commandHandler.handleContinuingTrace(command.paymentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.requests,
                            description = "Asks the payment provider to charge the customer."))
    public void capturePayment(CapturePayment command) {
        commandHandler.handle(command.paymentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description = "Records that the customer was charged."))
    public void confirmPaymentCapture(ConfirmPaymentCapture command) {
        commandHandler.handleContinuingTrace(command.paymentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description = "Records that charging the customer failed."))
    public void failPaymentCapture(FailPaymentCapture command) {
        commandHandler.handleContinuingTrace(command.paymentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.requests,
                            description = "Cancels the authorized payment, so the customer is never charged."))
    public void cancelPayment(CancelPayment command) {
        commandHandler.handle(command.paymentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description = "Fails the payment, because the customer did not pay in time."))
    public void timeOutPayment(TimeOutPayment command) {
        commandHandler.handleContinuingTrace(command.paymentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = PaymentsConfig.internal,
                            description =
                                    "Fails the payment, because its authorization ran out before the shop charged the customer."))
    public void expirePaymentAuthorization(ExpirePaymentAuthorization command) {
        commandHandler.handleContinuingTrace(command.paymentId(), command);
    }
}
