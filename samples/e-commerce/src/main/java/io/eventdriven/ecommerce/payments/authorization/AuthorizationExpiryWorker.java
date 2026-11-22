package io.eventdriven.ecommerce.payments.authorization;

import static io.eventdriven.ecommerce.payments.PaymentCommand.ExpirePaymentAuthorization;

import io.eventdriven.core.messaging.MessageBus;
import java.time.OffsetDateTime;
import java.util.List;

public class AuthorizationExpiryWorker {
    private final AuthorizedPayments authorizedPayments;
    private final MessageBus messageBus;

    public AuthorizationExpiryWorker(AuthorizedPayments authorizedPayments, MessageBus messageBus) {
        this.authorizedPayments = authorizedPayments;
        this.messageBus = messageBus;
    }

    public void handle(OffsetDateTime now) {
        for (var paymentId : authorizedPayments.expiredAt(now)) {
            messageBus.publish(List.of(new ExpirePaymentAuthorization(paymentId, now)));
        }
    }
}
