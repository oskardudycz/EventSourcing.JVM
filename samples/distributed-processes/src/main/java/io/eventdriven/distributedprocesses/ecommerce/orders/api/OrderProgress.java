package io.eventdriven.distributedprocesses.ecommerce.orders.api;

public enum OrderProgress {
  AwaitingPayment("Awaiting payment"),
  PaymentFailed("Payment failed"),
  Processing("Processing"),
  Shipped("Shipped"),
  Completed("Completed"),
  Canceled("Canceled"),
  ClosedUnpaid("Closed unpaid");

  private final String label;

  OrderProgress(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
