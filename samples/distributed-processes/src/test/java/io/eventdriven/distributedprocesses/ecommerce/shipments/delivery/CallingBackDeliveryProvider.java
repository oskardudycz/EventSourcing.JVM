package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import java.util.ArrayList;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.DeliverPackage;

// Stands in for a provider that books the parcel at once and calls back when it is delivered.
// The parcel is booked before the shipment records it as sent, so the call back cannot come
// from inside the booking: tests decide when the parcels arrive.
public class CallingBackDeliveryProvider implements DeliveryProvider {
  public static final ParcelBooking booking = new ParcelBooking("UPS", "1Z999AA10123456784");

  private final CommandBus commandBus;
  private final List<ShipmentId> booked = new ArrayList<>();

  public CallingBackDeliveryProvider(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  @Override
  public ParcelBooking book(ShipmentId shipmentId, ProductItem[] productItems) {
    booked.add(shipmentId);

    return booking;
  }

  public void deliverBookedParcels() {
    var delivered = List.copyOf(booked);
    booked.clear();

    for (var shipmentId : delivered) {
      commandBus.send(new DeliverPackage(shipmentId));
    }
  }
}
