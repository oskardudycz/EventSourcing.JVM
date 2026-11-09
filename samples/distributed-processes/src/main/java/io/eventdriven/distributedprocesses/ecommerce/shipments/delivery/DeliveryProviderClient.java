package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DeliveryProviderClient {
  private static final Logger logger = LoggerFactory.getLogger(DeliveryProviderClient.class);

  private final DeliveryProvider deliveryProvider;

  public DeliveryProviderClient(DeliveryProvider deliveryProvider) {
    this.deliveryProvider = deliveryProvider;
  }

  public Optional<ParcelBooking> book(ShipmentId shipmentId, ProductItem[] productItems) {
    try {
      return Optional.of(deliveryProvider.book(shipmentId, productItems));
    } catch (Exception failedBooking) {
      // The process has no "booking failed" event, so there is nothing to record here:
      // the shipment stays reserved until someone sends it again or the reservation expires.
      logger.error(
        "Booking a parcel for shipment %s with the delivery provider failed".formatted(shipmentId),
        failedBooking
      );

      return Optional.empty();
    }
  }
}
