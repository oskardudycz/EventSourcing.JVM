package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.ParcelBookingRequested;

public class DeliveryProviderClient {
  private static final Logger logger = LoggerFactory.getLogger(DeliveryProviderClient.class);

  private final DeliveryProvider deliveryProvider;

  public DeliveryProviderClient(DeliveryProvider deliveryProvider) {
    this.deliveryProvider = deliveryProvider;
  }

  public void on(ParcelBookingRequested event) {
    try {
      deliveryProvider.book(event.shipmentId(), event.productItems());
    } catch (Exception failedBooking) {
      // The process has no "booking failed" event, so there is nothing to record here:
      // the stock stays held until someone sends the package again or the reservation expires.
      logger.error(
        "Booking a parcel for shipment %s with the delivery provider failed".formatted(event.shipmentId()),
        failedBooking
      );
    }
  }
}
