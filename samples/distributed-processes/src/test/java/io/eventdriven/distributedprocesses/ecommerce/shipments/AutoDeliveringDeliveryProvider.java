package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;


import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.DeliverPackage;

// Stands in for a provider that delivers the parcel and calls back straight away.
public class AutoDeliveringDeliveryProvider implements DeliveryProvider {
  private final CommandBus commandBus;

  public AutoDeliveringDeliveryProvider(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  @Override
  public void deliver(ShipmentId shipmentId, ProductItem[] productItems) {
    commandBus.send(new DeliverPackage(shipmentId));
  }
}
