package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

public record CheckoutSession(URI url, OffsetDateTime expiresAt) {
}
