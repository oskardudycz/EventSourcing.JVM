package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;

@Schema(description = "Opens the payment provider's page, where the customer pays.")
public record CreateCheckoutSession(PaymentId paymentId, String referenceId, URI successUrl, URI cancelUrl) {}
