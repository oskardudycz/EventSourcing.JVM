package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

public record ParcelBooking(
  String carrier,
  String trackingNumber
) {
}
