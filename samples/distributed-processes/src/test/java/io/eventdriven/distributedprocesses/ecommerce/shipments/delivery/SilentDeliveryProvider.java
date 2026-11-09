package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

// Stands in for a provider that has booked the parcel but not delivered it yet.
public class SilentDeliveryProvider implements DeliveryProvider {
  public static final ParcelBooking booking = new ParcelBooking("DHL", "JD014600006281234567");

  @Override
  public ParcelBooking book(ShipmentId shipmentId, ProductItem[] productItems) {
    return booking;
  }
}
