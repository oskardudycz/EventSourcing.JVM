package io.eventdriven.distributedprocesses.ecommerce.payments;

public class PaymentGatewayException extends RuntimeException {
  public PaymentGatewayException(String message, Throwable cause) {
    super(message, cause);
  }
}
