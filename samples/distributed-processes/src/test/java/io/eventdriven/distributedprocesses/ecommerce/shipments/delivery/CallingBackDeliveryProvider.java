package io.eventdriven.distributedprocesses.ecommerce.shipments.delivery;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import java.util.ArrayList;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ConfirmParcelBooking;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.DeliverPackage;

// Stands in for a provider that answers when the test says so: first with the booking,
// then with the delivery.
public class CallingBackDeliveryProvider implements DeliveryProvider {
  public static final String carrier = "UPS";
  public static final String trackingNumber = "1Z999AA10123456784";

  private final CommandBus commandBus;
  private final List<ShipmentId> requested = new ArrayList<>();
  private final List<ShipmentId> booked = new ArrayList<>();

  public CallingBackDeliveryProvider(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  @Override
  public void book(ShipmentId shipmentId, ProductItem[] productItems) {
    requested.add(shipmentId);
  }

  public void confirmBookings() {
    var confirmed = List.copyOf(requested);
    requested.clear();
    booked.addAll(confirmed);

    for (var shipmentId : confirmed) {
      commandBus.send(new ConfirmParcelBooking(shipmentId, carrier, trackingNumber));
    }
  }

  public void deliverParcels() {
    var delivered = List.copyOf(booked);
    booked.clear();

    for (var shipmentId : delivered) {
      commandBus.send(new DeliverPackage(shipmentId));
    }
  }
}
