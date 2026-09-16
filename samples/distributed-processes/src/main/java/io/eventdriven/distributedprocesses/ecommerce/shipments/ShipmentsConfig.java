package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEventForwarder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

public final class ShipmentsConfig {
  private ShipmentsConfig() {
  }

  public static ShipmentsModule configure(
    CommandBus commandBus,
    EventStore eventStore,
    InternalEventBus internalEventBus,
    IntegrationEventBus integrationEventBus,
    DeliveryProvider deliveryProvider,
    Function<ProductItem, Boolean> isProductAvailable,
    Duration reservationValidity,
    Supplier<OffsetDateTime> now
  ) {
    var store = new AggregateStore<Shipment, ShipmentEvent, ShipmentId>(
      eventStore,
      ShipmentFacade::mapToStreamId,
      Shipment::empty
    );

    var facade = new ShipmentFacade(store, isProductAvailable, reservationValidity, now);

    commandBus
      .handle(ReserveStock.class, facade::reserveStock)
      .handle(SendPackage.class, facade::sendPackage)
      .handle(DeliverPackage.class, facade::deliverPackage)
      .handle(ReleaseStock.class, facade::releaseStock)
      .handle(ExpireStockReservation.class, facade::expireStockReservation);

    var forwarder = new ShipmentExternalEventForwarder(integrationEventBus);

    // Forwarding first, so that handing the parcel over does not publish the delivery
    // before the dispatch that caused it.
    internalEventBus
      .subscribe(StockReserved.class, forwarder::on)
      .subscribe(ProductWasOutOfStock.class, forwarder::on)
      .subscribe(PackageWasSent.class, forwarder::on)
      .subscribe(PackageWasDelivered.class, forwarder::on)
      .subscribe(StockReservationExpired.class, forwarder::on);

    var stockReservations = new StockReservations();

    internalEventBus
      .subscribe(StockReserved.class, stockReservations::on)
      .subscribe(PackageWasSent.class, stockReservations::on)
      .subscribe(StockReleased.class, stockReservations::on)
      .subscribe(StockReservationExpired.class, stockReservations::on);

    var deliveryProviderClient = new DeliveryProviderClient(deliveryProvider);

    internalEventBus
      .subscribe(PackageWasSent.class, deliveryProviderClient::on);

    return new ShipmentsModule(
      facade,
      new ReservationExpiryWorker(stockReservations, commandBus)
    );
  }
}
