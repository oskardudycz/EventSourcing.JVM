package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.core.messaging.InternalEventBus;
import io.eventdriven.distributedprocesses.core.projections.Database;
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

    var database = new Database();
    var summaries = new ShipmentSummaries(database);

    // The projections go first: they are the module's own read side, and both the forwarder
    // and the expiry worker read them.
    internalEventBus
      .subscribeWithMetadata(StockReserved.class, summaries::onStockReserved)
      .subscribeWithMetadata(ProductWasOutOfStock.class, summaries::onProductWasOutOfStock)
      .subscribeWithMetadata(PackageWasSent.class, summaries::onPackageWasSent)
      .subscribeWithMetadata(PackageWasDelivered.class, summaries::onPackageWasDelivered)
      .subscribeWithMetadata(StockReleased.class, summaries::onStockReleased)
      .subscribeWithMetadata(StockReservationExpired.class, summaries::onStockReservationExpired);

    var stockReservations = new StockReservations(database);

    internalEventBus
      .subscribeWithMetadata(StockReserved.class, stockReservations::onStockReserved)
      .subscribeWithMetadata(PackageWasSent.class, stockReservations::onPackageWasSent)
      .subscribeWithMetadata(StockReleased.class, stockReservations::onStockReleased)
      .subscribeWithMetadata(StockReservationExpired.class, stockReservations::onStockReservationExpired);

    var forwarder = new ShipmentExternalEventForwarder(summaries, integrationEventBus);

    // Forwarding before the delivery provider, so that handing the parcel over does not
    // publish the delivery before the dispatch that caused it.
    internalEventBus
      .subscribe(StockReserved.class, forwarder::on)
      .subscribe(ProductWasOutOfStock.class, forwarder::on)
      .subscribe(PackageWasSent.class, forwarder::on)
      .subscribe(PackageWasDelivered.class, forwarder::on)
      .subscribe(StockReservationExpired.class, forwarder::on);

    var deliveryProviderClient = new DeliveryProviderClient(deliveryProvider);

    internalEventBus
      .subscribe(PackageWasSent.class, deliveryProviderClient::on);

    return new ShipmentsModule(
      facade,
      new ReservationExpiryWorker(stockReservations, commandBus)
    );
  }
}
